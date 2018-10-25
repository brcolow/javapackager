/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.openjfx.tools.packager.mac;

import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;
import static com.sun.openjfx.tools.packager.mac.MacAppBundler.*;

public class MacAppStoreBundler extends MacBaseInstallerBundler {

    private static final String TEMPLATE_BUNDLE_ICON_HIDPI = "/packager/mac/GenericAppHiDPI.icns";
    private static final String DEFAULT_ENTITLEMENTS = "/packager/mac/MacAppStore.entitlements";
    private static final String DEFAULT_INHERIT_ENTITLEMENTS = "/packager/mac/MacAppStore_Inherit.entitlements";

    public static final BundlerParamInfo<String> MAC_APP_STORE_APP_SIGNING_KEY = new StandardBundlerParam<>(
            "Application Signing Key",
            "The full name of the signing key to sign the application with.",
            "mac.signing-key-app",
            String.class,
            params -> {
                    String result = MacBaseInstallerBundler.findKey("3rd Party Mac Developer Application: " + SIGNING_KEY_USER.fetchFrom(params),
                                                                    SIGNING_KEYCHAIN.fetchFrom(params),
                                                                    VERBOSE.fetchFrom(params));
                    if (result != null) {
                        MacCertificate certificate = new MacCertificate(result, VERBOSE.fetchFrom(params));

                        if (!certificate.isValid()) {
                            Log.info(MessageFormat.format("Error: Certificate expired {0}.", result));
                        }
                    }

                    return result;
                },
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_APP_STORE_PKG_SIGNING_KEY = new StandardBundlerParam<>(
            "Installer Signing Key",
            "The full name of the signing key to sign the PKG Installer with.",
            "mac.signing-key-pkg",
            String.class,
            params -> {
                    String result = MacBaseInstallerBundler.findKey("3rd Party Mac Developer Installer: " + SIGNING_KEY_USER.fetchFrom(params), SIGNING_KEYCHAIN.fetchFrom(params), VERBOSE.fetchFrom(params));

                    if (result != null) {
                        MacCertificate certificate = new MacCertificate(result, VERBOSE.fetchFrom(params));

                        if (!certificate.isValid()) {
                            Log.info(MessageFormat.format("Error: Certificate expired {0}.", result));
                        }
                    }

                    return result;
                },
            (s, p) -> s);

    public static final StandardBundlerParam<File> MAC_APP_STORE_ENTITLEMENTS  = new StandardBundlerParam<>(
            "Mac App Store Entitlements",
            "Mac App Store Inherit Entitlements",
            "mac.app-store-entitlements",
            File.class,
            params -> null,
            (s, p) -> new File(s));

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX = new StandardBundlerParam<> (
            "Installer Suffix",
            "The suffix for the installer name for this package.  <name><suffix>.pkg.",
            "mac.app-store.installerName.suffix",
            String.class,
            params -> "-MacAppStore",
            (s, p) -> s);

    public MacAppStoreBundler() {
        super();
        baseResourceLoader = MacResources.class;
    }

    //@Override
    public File bundle(Map<String, ? super Object> p, File outdir) {
        Log.info(MessageFormat.format("Building Mac App Store Bundle for {0}", APP_NAME.fetchFrom(p)));
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outdir.getAbsolutePath()));
        }

        // first, load in some overrides
        // icns needs @2 versions, so load in the @2 default
        p.put(DEFAULT_ICNS_ICON.getID(), TEMPLATE_BUNDLE_ICON_HIDPI);

        // next we need to change the jdk/jre stripping to strip gstreamer
//        p.put(MAC_RULES.getID(), createMacAppStoreRuntimeRules(p));

        // now we create the app
        File appImageDir = APP_IMAGE_BUILD_ROOT.fetchFrom(p);
        try {
            appImageDir.mkdirs();

            try {
                MacAppImageBuilder.addNewKeychain(p);
            } catch (InterruptedException e) {
                Log.error(e.getMessage());
            }
            // first, make sure we don't use the local signing key
            p.put(DEVELOPER_ID_APP_SIGNING_KEY.getID(), null);
            File appLocation = prepareAppBundle(p);

            prepareEntitlements(p);

            String signingIdentity = MAC_APP_STORE_APP_SIGNING_KEY.fetchFrom(p);
            String identifierPrefix = BUNDLE_ID_SIGNING_PREFIX.fetchFrom(p);
            String entitlementsFile = getConfig_Entitlements(p).toString();
            String inheritEntitlements = getConfig_Inherit_Entitlements(p).toString();

            MacAppImageBuilder.signAppBundle(p, appLocation.toPath(), signingIdentity, identifierPrefix, entitlementsFile, inheritEntitlements);
            MacAppImageBuilder.restoreKeychainList(p);

            ProcessBuilder pb;

            // create the final pkg file
            File finalPKG = new File(outdir, INSTALLER_NAME.fetchFrom(p)
                    + INSTALLER_SUFFIX.fetchFrom(p)
                    + ".pkg");
            outdir.mkdirs();

            String installIdentify = MAC_APP_STORE_PKG_SIGNING_KEY.fetchFrom(p);

            List<String> buildOptions = new ArrayList<>();
            buildOptions.add("productbuild");
            buildOptions.add("--component");
            buildOptions.add(appLocation.toString());
            buildOptions.add("/Applications");
            buildOptions.add("--sign");
            buildOptions.add(installIdentify);
            buildOptions.add("--product");
            buildOptions.add(appLocation + "/Contents/Info.plist");
            String keychainName = SIGNING_KEYCHAIN.fetchFrom(p);
            if (keychainName != null && !keychainName.isEmpty()) {
                buildOptions.add("--keychain");
                buildOptions.add(keychainName);
            }
            buildOptions.add(finalPKG.getAbsolutePath());

            pb = new ProcessBuilder(buildOptions);

            IOUtils.exec(pb, VERBOSE.fetchFrom(p));
            return finalPKG;
        } catch (Exception ex) {
            Log.info("App Store Ready Bundle failed : " + ex.getMessage());
            ex.printStackTrace();
            Log.debug(ex);
            return null;
        } finally {
            try {
                if (appImageDir != null && !Log.isDebug()) {
                    IOUtils.deleteRecursive(appImageDir);
                } else if (appImageDir != null) {
                    Log.info(MessageFormat.format("Intermediate application bundle image: {0}",
                            appImageDir.getAbsolutePath()));
                }
                if (!VERBOSE.fetchFrom(p)) {
                    // cleanup
                    cleanupConfigFiles(p);
                } else {
                    Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                            CONFIG_ROOT.fetchFrom(p).getAbsolutePath()));
                }
            } catch (IOException ex) {
                Log.debug(ex.getMessage());
                // noinspection ReturnInsideFinallyBlock
                return null;
            }
        }
    }

    protected void cleanupConfigFiles(Map<String, ? super Object> params) {
        if (getConfig_Entitlements(params) != null) {
            getConfig_Entitlements(params).delete();
        }
        if (getConfig_Inherit_Entitlements(params) != null) {
            getConfig_Inherit_Entitlements(params).delete();
        }
        if (MAC_APP_IMAGE.fetchFrom(params) == null) {
            APP_BUNDLER.fetchFrom(params).cleanupConfigFiles(params);
        }
    }

    private File getConfig_Entitlements(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_NAME.fetchFrom(params) + ".entitlements");
    }

    private File getConfig_Inherit_Entitlements(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_NAME.fetchFrom(params) + "_Inherit.entitlements");
    }

    private void prepareEntitlements(Map<String, ? super Object> params) throws IOException {
        File entitlements = MAC_APP_STORE_ENTITLEMENTS.fetchFrom(params);
        if (entitlements == null || !entitlements.exists()) {
            fetchResource(getEntitlementsFileName(params),
                    "Mac App Store Entitlements",
                    DEFAULT_ENTITLEMENTS,
                    getConfig_Entitlements(params),
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        } else {
            fetchResource(getEntitlementsFileName(params),
                    "Mac App Store Entitlements",
                    entitlements,
                    getConfig_Entitlements(params),
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        }
        fetchResource(getInheritEntitlementsFileName(params),
                "Mac App Store Inherit Entitlements",
                DEFAULT_INHERIT_ENTITLEMENTS,
                getConfig_Inherit_Entitlements(params),
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
    }

    private String getEntitlementsFileName(Map<String, ? super Object> params) {
        return MAC_BUNDLER_PREFIX+ APP_NAME.fetchFrom(params) +".entitlements";
    }

    private String getInheritEntitlementsFileName(Map<String, ? super Object> params) {
        return MAC_BUNDLER_PREFIX+ APP_NAME.fetchFrom(params) +"_Inherit.entitlements";
    }


    //////////////////////////////////////////////////////////////////////////////////
    // Implement Bundler
    //////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getName() {
        return "Mac App Store Ready Bundler";
    }

    @Override
    public String getDescription() {
        return "Creates a binary bundle ready for deployment into the Mac App Store.";
    }

    @Override
    public String getID() {
        return "mac.appStore";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(getAppBundleParameters());
        results.addAll(getMacAppStoreBundleParameters());
        return results;
    }

    public Collection<BundlerParamInfo<?>> getMacAppStoreBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();

        results.addAll(getAppBundleParameters());
        results.remove(DEVELOPER_ID_APP_SIGNING_KEY);
        results.addAll(Arrays.asList(
                INSTALLER_SUFFIX,
                MAC_APP_STORE_APP_SIGNING_KEY,
                MAC_APP_STORE_ENTITLEMENTS,
                MAC_APP_STORE_PKG_SIGNING_KEY,
                SIGNING_KEYCHAIN
        ));

        return results;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (Platform.getPlatform() != Platform.MAC) {
                throw new UnsupportedPlatformException();
            }

            if (params == null) {
                throw new ConfigException(
                        "Parameters map is null.",
                        "Pass in a non-null parameters map.");
            }

            // hdiutil is always available so there's no need to test for availability.
            //run basic validation to ensure requirements are met

            //TODO Mac App Store apps cannot use the system runtime

            //we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            // reject explicitly set to not sign
            if (!Optional.ofNullable(SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
                throw new ConfigException(
                        "Mac App Store apps must be signed, and signing has been disabled by bundler configuration.",
                        "Either unset 'signBundle' or set 'signBundle' to true.");
            }

            // make sure we have settings for signatures
            if (MAC_APP_STORE_APP_SIGNING_KEY.fetchFrom(params) == null) {
                throw new ConfigException(
                        "No Mac App Store App Signing Key",
                        "Install your app signing keys into your Mac Keychain using XCode.");
            }
            if (MAC_APP_STORE_PKG_SIGNING_KEY.fetchFrom(params) == null) {
                throw new ConfigException(
                        "No Mac App Store Installer Signing Key",
                        "Install your app signing keys into your Mac Keychain using XCode.");
            }

            // things we could check...
            // check the icons, make sure it has hidpi icons
            // check the category, make sure it fits in the list apple has provided
            // validate bundle identifier is reverse dns
            //  check for \a+\.\a+\..

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }
}
