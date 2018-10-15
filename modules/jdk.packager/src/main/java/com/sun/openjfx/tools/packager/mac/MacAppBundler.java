/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.openjfx.tools.packager.AbstractImageBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.EnumeratedBundlerParam;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import com.sun.openjfx.tools.packager.JLinkBundlerHelper;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;
import static com.sun.openjfx.tools.packager.mac.MacBaseInstallerBundler.*;
import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;

public class MacAppBundler extends AbstractImageBundler {

    public final static String MAC_BUNDLER_PREFIX =
            BUNDLER_PREFIX + "macosx" + File.separator;

    private static final String TEMPLATE_BUNDLE_ICON = "GenericApp.icns";

    private static Map<String, String> getMacCategories() {
        Map<String, String> map = new HashMap<>();
        map.put("Business", "public.app-category.business");
        map.put("Developer Tools", "public.app-category.developer-tools");
        map.put("Education", "public.app-category.education");
        map.put("Entertainment", "public.app-category.entertainment");
        map.put("Finance", "public.app-category.finance");
        map.put("Games", "public.app-category.games");
        map.put("Graphics & Design", "public.app-category.graphics-design");
        map.put("Healthcare & Fitness", "public.app-category.healthcare-fitness");
        map.put("Lifestyle", "public.app-category.lifestyle");
        map.put("Medical", "public.app-category.medical");
        map.put("Music", "public.app-category.music");
        map.put("News", "public.app-category.news");
        map.put("Photography", "public.app-category.photography");
        map.put("Productivity", "public.app-category.productivity");
        map.put("Reference", "public.app-category.reference");
        map.put("Social Networking", "public.app-category.social-networking");
        map.put("Sports", "public.app-category.sports");
        map.put("Travel", "public.app-category.travel");
        map.put("Utilities", "public.app-category.utilities");
        map.put("Video", "public.app-category.video");
        map.put("Weather", "public.app-category.weather");

        map.put("Action Games", "public.app-category.action-games");
        map.put("Adventure Games", "public.app-category.adventure-games");
        map.put("Arcade Games", "public.app-category.arcade-games");
        map.put("Board Games", "public.app-category.board-games");
        map.put("Card Games", "public.app-category.card-games");
        map.put("Casino Games", "public.app-category.casino-games");
        map.put("Dice Games", "public.app-category.dice-games");
        map.put("Educational Games", "public.app-category.educational-games");
        map.put("Family Games", "public.app-category.family-games");
        map.put("Kids Games", "public.app-category.kids-games");
        map.put("Music Games", "public.app-category.music-games");
        map.put("Puzzle Games", "public.app-category.puzzle-games");
        map.put("Racing Games", "public.app-category.racing-games");
        map.put("Role Playing Games", "public.app-category.role-playing-games");
        map.put("Simulation Games", "public.app-category.simulation-games");
        map.put("Sports Games", "public.app-category.sports-games");
        map.put("Strategy Games", "public.app-category.strategy-games");
        map.put("Trivia Games", "public.app-category.trivia-games");
        map.put("Word Games", "public.app-category.word-games");

        return map;
    }

    public static final EnumeratedBundlerParam<String> MAC_CATEGORY =
            new EnumeratedBundlerParam<>(
                    "Category",
                    "Mac App Store Categories. Note that the key is the string to display to the user and the value " +
                            "is the id of the category.",
                    "mac.category",
                    String.class,
                    params -> params.containsKey(CATEGORY.getID())
                            ? CATEGORY.fetchFrom(params)
                            : "Unknown",
                    (s, p) -> s,
                    getMacCategories()
            );

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME =
            new StandardBundlerParam<>(
                    "CFBundleName",
                    "The name of the app as it appears in the Menu Bar.  This can be different from the application " +
                            "name.  This name should be less than 16 characters long and be suitable for displaying " +
                            "in the menu bar and the app's Info window.",
                    "mac.CFBundleName",
                    String.class,
                    params -> null,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER =
            new StandardBundlerParam<>(
                    "CFBundleIdentifier",
                    "An identifier that uniquely identifies the application for MacOSX (and on the Mac App Store).  " +
                            "May only use alphanumeric (A-Z,a-z,0-9), hyphen (-), and period (.) characters.",
                    "mac.CFBundleIdentifier",
                    String.class,
                    IDENTIFIER::fetchFrom,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_VERSION =
            new StandardBundlerParam<>(
                    "CFBundleVersion",
                    "A computer readable version for the CFBundle.  May contain only digits and from zero to two " +
                            "dots, such as \"1.8.1\" or \"100\".",
                    "mac.CFBundleVersion",
                    String.class,
                    p -> {
                        String s = VERSION.fetchFrom(p);
                        if (validCFBundleVersion(s)) {
                            return s;
                        } else {
                            return "100";
                        }
                    },
                    (s, p) -> s);

    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
            params -> {
                File configRoot = new File(BUILD_ROOT.fetchFrom(params), "macosx");
                configRoot.mkdirs();
                return configRoot;
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<String> DEFAULT_ICNS_ICON = new StandardBundlerParam<>(
            "Default Icon",
            "The Default Icon for when a user does not specify an icns file.",
            ".mac.default.icns",
            String.class,
            params -> TEMPLATE_BUNDLE_ICON,
            (s, p) -> s);

    public static final BundlerParamInfo<String> DEVELOPER_ID_APP_SIGNING_KEY = new StandardBundlerParam<>(
            "Apple Developer ID Application Signing Key",
            "The full name of the Apple Developer ID Application signing key.",
            "mac.signing-key-developer-id-app",
            String.class,
            params -> {
                    String result = MacBaseInstallerBundler.findKey("Developer ID Application: " + SIGNING_KEY_USER.fetchFrom(params),
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

    public static final BundlerParamInfo<String> BUNDLE_ID_SIGNING_PREFIX = new StandardBundlerParam<>(
            "Bundle Signing Prefix",
            "When signing the application bundle this value is prefixed to all components that need to be signed " +
                    "that don't have an existing CFBundleIdentifier.",
            "mac.bundle-id-signing-prefix",
            String.class,
            params -> IDENTIFIER.fetchFrom(params) + ".",
            (s, p) -> s);

    public static final BundlerParamInfo<File> ICON_ICNS = new StandardBundlerParam<>(
            ".icns Icon",
            "Icon for the application, in ICNS format.",
            "icon.icns",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".icns")) {
                    Log.info(MessageFormat.format("The specified icon \"{0}\" is not an ICNS file and will not be " +
                            "used.  The default icon will be used in it's place.", f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public MacAppBundler() {
        super();
        baseResourceLoader = MacResources.class;
    }

    public static boolean validCFBundleVersion(String v) {
        // CFBundleVersion (String - iOS, OS X) specifies the build version
        // number of the bundle, which identifies an iteration (released or
        // unreleased) of the bundle. The build version number should be a
        // string comprised of three non-negative, period-separated integers
        // with the first integer being greater than zero. The string should
        // only contain numeric (0-9) and period (.) characters. Leading zeros
        // are truncated from each integer and will be ignored (that is,
        // 1.02.3 is equivalent to 1.2.3). This key is not localizable.

        if (v == null) {
            return false;
        }

        String p[] = v.split("\\.");
        if (p.length > 3 || p.length < 1) {
            Log.verbose("Version sting may have between 1 and 3 numbers: 1, 1.2, 1.2.3.");
            return false;
        }

        try {
            BigInteger n = new BigInteger(p[0]);
            if (BigInteger.ONE.compareTo(n) > 0) {
                Log.verbose("The first number in a CFBundleVersion cannot be zero or negative.");
                return false;
            }
            if (p.length > 1) {
                n = new BigInteger(p[1]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose("Negative numbers are not allowed in version strings.");
                    return false;
                }
            }
            if (p.length > 2) {
                n = new BigInteger(p[2]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose("Negative numbers are not allowed in version strings.");
                    return false;
                }
            }
        } catch (NumberFormatException ne) {
            Log.verbose("Version strings can consist of only numbers and up to two dots.");
            Log.verbose(ne);
            return false;
        }

        return true;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            return doValidate(params);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    //to be used by chained bundlers, e.g. by EXE bundler to avoid
    // skipping validation if p.type does not include "image"
    public boolean doValidate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.MAC) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        if (getPredefinedImage(p) != null) {
            return true;
        }

        // validate short version
        if (!validCFBundleVersion(MAC_CF_BUNDLE_VERSION.fetchFrom(p))) {
            throw new ConfigException(
                    "Invalid CFBundleVersion - ''{0}''",
                    "Set a compatible 'appVersion' or set a 'mac.CFBundleVersion'.  Valid versions are one to three " +
                            "integers separated by dots.");
        }

        // reject explicitly set sign to true and no valid signature key
        if (Optional.ofNullable(SIGN_BUNDLE.fetchFrom(p)).orElse(Boolean.FALSE)) {
            String signingIdentity = DEVELOPER_ID_APP_SIGNING_KEY.fetchFrom(p);
            if (signingIdentity == null) {
                throw new ConfigException(
                        "Signature explicitly requested but no signing certificate specified.",
                        "Either specify a valid cert in 'mac.signing-key-developer-id-app' or unset 'signBundle' or " +
                                "set 'signBundle' to false.");
            }
        }

        return true;
    }

    private File getConfig_InfoPlist(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), "Info.plist");
    }

    private File getConfig_Icon(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_NAME.fetchFrom(params) + ".icns");
    }


    File doBundle(Map<String, ? super Object> p, File outputDirectory, boolean dependentTask) {
        try {
            if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
                throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                        outputDirectory.getAbsolutePath()));
            }
            if (!outputDirectory.canWrite()) {
                throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                        outputDirectory.getAbsolutePath()));
            }

            // Create directory structure
            File rootDirectory = new File(outputDirectory, APP_NAME.fetchFrom(p) + ".app");
            IOUtils.deleteRecursive(rootDirectory);
            rootDirectory.mkdirs();

            if (!dependentTask) {
                Log.info(MessageFormat.format("Creating app bundle: {0}", rootDirectory.getAbsolutePath()));
            }

            if (!p.containsKey(JLinkBundlerHelper.JLINK_BUILDER.getID())) {
                p.put(JLinkBundlerHelper.JLINK_BUILDER.getID(), "macapp-image-builder");
            }

            AbstractAppImageBuilder appBuilder = new MacAppImageBuilder(p, outputDirectory.toPath());
            JLinkBundlerHelper.execute(p, appBuilder);
            return rootDirectory;
        } catch (IOException ex) {
            Log.info(ex.toString());
            Log.verbose(ex);
            return null;
        } catch (Exception ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    public void cleanupConfigFiles(Map<String, ? super Object> params) {
        //Since building the app can be bypassed, make sure configRoot was set
        if (CONFIG_ROOT.fetchFrom(params) != null) {
            getConfig_Icon(params).delete();
            getConfig_InfoPlist(params).delete();
        }
    }

    @Override
    public String getName() {
        return "Mac Application Image";
    }

    @Override
    public String getDescription() {
        return "A Directory based image of a mac Application with an optionally co-bundled JRE.  Used as a base for " +
                "the Installer bundlers";
    }

    @Override
    public String getID() {
        return "mac.app";
    }

    @Override
    public String getBundleType() {
        return "IMAGE";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        return getAppBundleParameters();
    }

    public static Collection<BundlerParamInfo<?>> getAppBundleParameters() {
        return Arrays.asList(
                APP_NAME,
                APP_RESOURCES,
                ARGUMENTS,
                BUNDLE_ID_SIGNING_PREFIX,
                CLASSPATH,
                DEVELOPER_ID_APP_SIGNING_KEY,
                ICON_ICNS,
                JVM_OPTIONS,
                JVM_PROPERTIES,
                MAC_CATEGORY,
                MAC_CF_BUNDLE_IDENTIFIER,
                MAC_CF_BUNDLE_NAME,
                MAC_CF_BUNDLE_VERSION,
                MAIN_CLASS,
                MAIN_JAR,
                PREFERENCES_ID,
                PRELOADER_CLASS,
                SIGNING_KEYCHAIN,
                USER_JVM_OPTIONS,
                VERSION
        );
    }


    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }

//    private void createLauncherForEntryPoint(Map<String, ? super Object> p, File rootDirectory) throws IOException {
//        prepareConfigFiles(p);
//
//        if (LAUNCHER_CFG_FORMAT.fetchFrom(p).equals(CFG_FORMAT_PROPERTIES)) {
//            writeCfgFile(p, rootDirectory);
//        } else {
//            writeCfgFile(p, new File(rootDirectory, getLauncherCfgName(p)), "$APPDIR/PlugIns/Java.runtime");
//        }
//
//        // Copy executable root folder
//        File executableFile = new File(rootDirectory, "Contents/MacOS/" + getLauncherName(p));
//        IOUtils.copyFromURL(
//                RAW_EXECUTABLE_URL.fetchFrom(p),
//                executableFile);
//        executableFile.setExecutable(true, false);
//
//    }
//

}
