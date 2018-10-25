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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.IDENTIFIER;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SERVICE_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SIGN_BUNDLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;

public class MacPkgBundler extends MacBaseInstallerBundler {

    private static final String MAC_BUNDLER_PREFIX = BUNDLER_PREFIX + "mac" + File.separator;
    private static final String DEFAULT_BACKGROUND_IMAGE = "/packager/mac/background_pkg.png";
    private static final String TEMPLATE_PREINSTALL_SCRIPT = "/packager/mac/preinstall.template";
    private static final String TEMPLATE_POSTINSTALL_SCRIPT = "/packager/mac/postinstall.template";

    private static final BundlerParamInfo<File> PACKAGES_ROOT = new StandardBundlerParam<>(
            "",
            "This is temporary location for component packages (application and daemon). The packages are " +
                    "incorporated into final product package.",
            "mac.pkg.packagesRoot",
            File.class,
        params -> {
            File packagesRoot = new File(BUILD_ROOT.fetchFrom(params), "packages");
            packagesRoot.mkdirs();
            return packagesRoot;
        },
        (s, p) -> new File(s));


    private final BundlerParamInfo<File> SCRIPTS_DIR = new StandardBundlerParam<>(
            "",
            "This is temporary location for package scripts.",
            "mac.pkg.scriptsDir",
            File.class,
        params -> {
            File scriptsDir = new File(CONFIG_ROOT.fetchFrom(params), "scripts");
            scriptsDir.mkdirs();
            return scriptsDir;
        },
        (s, p) -> new File(s));

    public static final BundlerParamInfo<String> DEVELOPER_ID_INSTALLER_SIGNING_KEY = new StandardBundlerParam<>(
            "Apple Developer ID Installer Signing Key",
            "The full name of the Apple Developer ID Installer signing key.",
            "mac.signing-key-developer-id-installer",
            String.class,
        params -> {
            String result = MacBaseInstallerBundler.findKey(
                    "Developer ID Installer: " + SIGNING_KEY_USER.fetchFrom(params),
                    SIGNING_KEYCHAIN.fetchFrom(params), VERBOSE.fetchFrom(params));
            if (result != null) {
                MacCertificate certificate = new MacCertificate(result, VERBOSE.fetchFrom(params));

                if (!certificate.isValid()) {
                    Log.info(MessageFormat.format("Error: Certificate expired {0}.", result));
                }
            }

            return result;
        },
        (s, p) -> s);

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX = new StandardBundlerParam<>(
            "Installer Suffix",
            "The suffix for the installer name for this package.  <name><suffix>.pkg.",
            "mac.pkg.installerName.suffix",
            String.class,
        params -> "",
        (s, p) -> s);

    public MacPkgBundler() {
        super();
        baseResourceLoader = MacResources.class;
    }

    public File bundle(Map<String, ? super Object> params, File outdir) {
        Log.info(MessageFormat.format("Building PKG package for {0}", APP_NAME.fetchFrom(params)));
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outdir.getAbsolutePath()));
        }

        File appImageDir = null;
        try {
            appImageDir = prepareAppBundle(params);

            if (appImageDir != null && prepareConfigFiles(params)) {
                if (SERVICE_HINT.fetchFrom(params)) {
                    File daemonImageDir = DAEMON_IMAGE_BUILD_ROOT.fetchFrom(params);
                    daemonImageDir.mkdirs();
                    prepareDaemonBundle(params);
                }

                File configScript = getConfig_Script(params);
                if (configScript.exists()) {
                    Log.info(MessageFormat.format("Running shell script on application image [{0}]",
                            configScript.getAbsolutePath()));
                    IOUtils.run("bash", configScript, VERBOSE.fetchFrom(params));
                }

                return createPKG(params, outdir, appImageDir);
            }
            return null;
        } catch (IOException ex) {
            Log.verbose(ex);
            return null;
        } finally {
            try {
                if (appImageDir != null && !Log.isDebug()) {
                    IOUtils.deleteRecursive(appImageDir);
                } else if (appImageDir != null) {
                    Log.info(MessageFormat.format("[DEBUG] Intermediate application bundle image: {0}",
                            appImageDir.getAbsolutePath()));
                }
                if (!VERBOSE.fetchFrom(params)) {
                    // cleanup
                    cleanupConfigFiles(params);
                } else {
                    Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                            CONFIG_ROOT.fetchFrom(params).getAbsolutePath()));
                }
            } catch (IOException ex) {
                Log.debug(ex);
                // noinspection ReturnInsideFinallyBlock
                return null;
            }
        }
    }

    private File getPackages_AppPackage(Map<String, ? super Object> params) {
        return new File(PACKAGES_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-app.pkg");
    }

    private File getPackages_DaemonPackage(Map<String, ? super Object> params) {
        return new File(PACKAGES_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-daemon.pkg");
    }

    private void cleanupPackagesFiles(Map<String, ? super Object> params) {
        if (getPackages_AppPackage(params) != null) {
            getPackages_AppPackage(params).delete();
        }
        if (getPackages_DaemonPackage(params) != null) {
            getPackages_DaemonPackage(params).delete();
        }
    }

    private File getConfig_DistributionXMLFile(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), "distribution.dist");
    }

    private File getConfig_BackgroundImage(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-background.png");
    }

    private File getScripts_PreinstallFile(Map<String, ? super Object> params) {
        return new File(SCRIPTS_DIR.fetchFrom(params), "preinstall");
    }

    private File getScripts_PostinstallFile(Map<String, ? super Object> params) {
        return new File(SCRIPTS_DIR.fetchFrom(params), "postinstall");
    }

    private void cleanupPackageScripts(Map<String, ? super Object> params) {
        if (getScripts_PreinstallFile(params) != null) {
            getScripts_PreinstallFile(params).delete();
        }
        if (getScripts_PostinstallFile(params) != null) {
            getScripts_PostinstallFile(params).delete();
        }
    }

    private void cleanupConfigFiles(Map<String, ? super Object> params) {
        if (getConfig_DistributionXMLFile(params) != null) {
            getConfig_DistributionXMLFile(params).delete();
        }
        if (getConfig_BackgroundImage(params) != null) {
            getConfig_BackgroundImage(params).delete();
        }
    }

    private String getAppIdentifier(Map<String, ? super Object> params) {
        return IDENTIFIER.fetchFrom(params);
    }

    private String getDaemonIdentifier(Map<String, ? super Object> params) {
        return IDENTIFIER.fetchFrom(params) + ".daemon";
    }

    private void preparePackageScripts(Map<String, ? super Object> params) throws IOException {
        Log.verbose("Preparing package scripts");

        Map<String, String> data = new HashMap<>();

        data.put("DEPLOY_DAEMON_IDENTIFIER", getDaemonIdentifier(params));
        data.put("DEPLOY_LAUNCHD_PLIST_FILE",
                IDENTIFIER.fetchFrom(params).toLowerCase() + ".launchd.plist");

        Writer w = new BufferedWriter(new FileWriter(getScripts_PreinstallFile(params)));
        String content = preprocessTextResource(MAC_BUNDLER_PREFIX + getScripts_PreinstallFile(params).getName(),
                "PKG preinstall script",
                TEMPLATE_PREINSTALL_SCRIPT,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();
        getScripts_PreinstallFile(params).setExecutable(true, false);

        w = new BufferedWriter(new FileWriter(getScripts_PostinstallFile(params)));
        content = preprocessTextResource(MAC_BUNDLER_PREFIX + getScripts_PostinstallFile(params).getName(),
                "PKG postinstall script",
                TEMPLATE_POSTINSTALL_SCRIPT,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();
        getScripts_PostinstallFile(params).setExecutable(true, false);
    }

    private void prepareDistributionXMLFile(Map<String, ? super Object> params) throws IOException {
        File f = getConfig_DistributionXMLFile(params);

        Log.verbose(MessageFormat.format("Preparing distribution.dist: {0}", f.getAbsolutePath()));

        PrintStream out = new PrintStream(f);

        out.println("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>");
        out.println("<installer-gui-script minSpecVersion=\"1\">");

        out.println("<title>" + APP_NAME.fetchFrom(params) + "</title>");
        out.println("<background" +
                " file=\"" + getConfig_BackgroundImage(params).getName() + "\"" +
                " mime-type=\"image/png\"" +
                " alignment=\"bottomleft\" " +
                " scaling=\"none\"" +
                "/>");

        if (!LICENSE_FILE.fetchFrom(params).isEmpty()) {
            File licFile = null;

            List<String> licFiles = LICENSE_FILE.fetchFrom(params);
            if (licFiles.isEmpty()) {
                return;
            }
            String licFileStr = licFiles.get(0);

            for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
                if (rfs.contains(licFileStr)) {
                    licFile = new File(rfs.getBaseDirectory(), licFileStr);
                    break;
                }
            }

            // this is NPE protection, validate should have caught it's absence
            // so we don't complain or throw an error
            if (licFile != null) {
                out.println("<license" +
                        " file=\"" + licFile.getAbsolutePath() + "\"" +
                        " mime-type=\"text/rtf\"" +
                        "/>");
            }
        }

        /*
         * Note that the content of the distribution file
         * below is generated by productbuild --synthesize
         */

        String appId = getAppIdentifier(params);
        String daemonId = getDaemonIdentifier(params);

        out.println("<pkg-ref id=\"" + appId + "\"/>");
        if (SERVICE_HINT.fetchFrom(params)) {
            out.println("<pkg-ref id=\"" + daemonId + "\"/>");
        }

        out.println("<options customize=\"never\" require-scripts=\"false\"/>");
        out.println("<choices-outline>");
        out.println("    <line choice=\"default\">");
        out.println("        <line choice=\"" + appId + "\"/>");
        if (SERVICE_HINT.fetchFrom(params)) {
            out.println("        <line choice=\"" + daemonId + "\"/>");
        }
        out.println("    </line>");
        out.println("</choices-outline>");
        out.println("<choice id=\"default\"/>");
        out.println("<choice id=\"" + appId + "\" visible=\"false\">");
        out.println("    <pkg-ref id=\"" + appId + "\"/>");
        out.println("</choice>");
        out.println("<pkg-ref id=\"" + appId + "\" version=\"" + VERSION.fetchFrom(params) +
                "\" onConclusion=\"none\">" +
                        URLEncoder.encode(getPackages_AppPackage(params).getName(), StandardCharsets.UTF_8) + "</pkg-ref>");

        if (SERVICE_HINT.fetchFrom(params)) {
            out.println("<choice id=\"" + daemonId + "\" visible=\"false\">");
            out.println("    <pkg-ref id=\"" + daemonId + "\"/>");
            out.println("</choice>");
            out.println("<pkg-ref id=\"" + daemonId + "\" version=\"" + VERSION.fetchFrom(params) +
                    "\" onConclusion=\"none\">" +
                    URLEncoder.encode(getPackages_DaemonPackage(params).getName(), StandardCharsets.UTF_8) + "</pkg-ref>");
        }

        out.println("</installer-gui-script>");

        out.close();
    }

    private boolean prepareConfigFiles(Map<String, ? super Object> params) throws IOException {
        File imageTarget = getConfig_BackgroundImage(params);
        fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + imageTarget.getName(),
                "pkg background image",
                DEFAULT_BACKGROUND_IMAGE,
                imageTarget,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));

        prepareDistributionXMLFile(params);

        fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + getConfig_Script(params).getName(),
                "script to run after application image is populated",
                (String) null,
                getConfig_Script(params),
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));

        return true;
    }

    // name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-post-image.sh");
    }

    private File createPKG(Map<String, ? super Object> params, File outdir, File appLocation) {
        // generic find attempt
        try {
            if (Platform.getMajorVersion() > 10 ||
                (Platform.getMajorVersion() == 10 && Platform.getMinorVersion() >= 12)) {
                // we need this for OS X 10.12+
                Log.info("Warning: For signing PKG, you might need to set \"Always Trust\" for your certificate " +
                        "using \"Keychain Access\" tool.");
            }
            String daemonLocation = DAEMON_IMAGE_BUILD_ROOT.fetchFrom(params) + "/" +
                    APP_FS_NAME.fetchFrom(params) + ".daemon";

            File appPkg = getPackages_AppPackage(params);
            File daemonPkg = getPackages_DaemonPackage(params);

            // build application package
            ProcessBuilder pb = new ProcessBuilder("pkgbuild",
                    "--component",
                    appLocation.toString(),
                    "--install-location",
                    "/Applications",
                    appPkg.getAbsolutePath());
            IOUtils.exec(pb, VERBOSE.fetchFrom(params));

            // build daemon package if requested
            if (SERVICE_HINT.fetchFrom(params)) {
                preparePackageScripts(params);

                pb = new ProcessBuilder("pkgbuild",
                        "--identifier",
                        APP_FS_NAME.fetchFrom(params) + ".daemon",
                        "--root",
                        daemonLocation,
                        "--scripts",
                        SCRIPTS_DIR.fetchFrom(params).getAbsolutePath(),
                        daemonPkg.getAbsolutePath());
                IOUtils.exec(pb, VERBOSE.fetchFrom(params));
            }

            // build final package
            outdir.mkdirs();

            List<String> commandLine = new ArrayList<>();
            commandLine.add("productbuild");

            commandLine.add("--resources");
            commandLine.add(CONFIG_ROOT.fetchFrom(params).getAbsolutePath());

            // maybe sign
            if (Optional.ofNullable(SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
                String signingIdentity = DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                if (signingIdentity != null) {
                    commandLine.add("--sign");
                    commandLine.add(signingIdentity);
                }

                String keychainName = SIGNING_KEYCHAIN.fetchFrom(params);
                if (keychainName != null && !keychainName.isEmpty()) {
                    commandLine.add("--keychain");
                    commandLine.add(keychainName);
                }
            }

            commandLine.add("--distribution");
            commandLine.add(getConfig_DistributionXMLFile(params).getAbsolutePath());
            commandLine.add("--package-path");
            commandLine.add(PACKAGES_ROOT.fetchFrom(params).getAbsolutePath());

            File finalPkg = new File(outdir, INSTALLER_NAME.fetchFrom(params) +
                    INSTALLER_SUFFIX.fetchFrom(params) + ".pkg");
            commandLine.add(finalPkg.getAbsolutePath());

            pb = new ProcessBuilder(commandLine);
            IOUtils.exec(pb, VERBOSE.fetchFrom(params));

            return finalPkg;
        } catch (Exception ex) {
            Log.verbose(ex);
            return null;
        } finally {
            if (!VERBOSE.fetchFrom(params)) {
                cleanupPackagesFiles(params);
                cleanupConfigFiles(params);

                if (SERVICE_HINT.fetchFrom(params)) {
                    cleanupPackageScripts(params);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "Mac PKG Installer";
    }

    @Override
    public String getDescription() {
        return "Mac PKG Installer Bundle.";
    }

    @Override
    public String getID() {
        return "pkg";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(getPKGBundleParameters());
        return results;
    }

    private Collection<BundlerParamInfo<?>> getPKGBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();

        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(Arrays.asList(
                DEVELOPER_ID_INSTALLER_SIGNING_KEY,
                //IDENTIFIER,
                INSTALLER_SUFFIX,
                LICENSE_FILE,
                //SERVICE_HINT,
                SIGNING_KEYCHAIN));

        return results;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) {
                throw new ConfigException("Parameters map is null.", "Pass in a non-null parameters map.");
            }

            //run basic validation to ensure requirements are met
            //we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            // validate license file, if used, exists in the proper place
            if (params.containsKey(LICENSE_FILE.getID())) {
                List<RelativeFileSet> appResourcesList = APP_RESOURCES_LIST.fetchFrom(params);
                for (String license : LICENSE_FILE.fetchFrom(params)) {
                    boolean found = false;
                    for (RelativeFileSet appResources : appResourcesList) {
                        found = found || appResources.contains(license);
                    }
                    if (!found) {
                        throw new ConfigException("Specified license file is missing.",
                                MessageFormat.format("Make sure that \"{0}\" references a file in the app resources, " +
                                        "and that it is relative to the basedir \"{1}\".", license));
                    }
                }
            }

            // reject explicitly set sign to true and no valid signature key
            if (Optional.ofNullable(SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                String signingIdentity = DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                if (signingIdentity == null) {
                    throw new ConfigException("Signature explicitly requested but no signing certificate specified.",
                            "Either specify a valid cert in 'mac.signing-key-developer-id-installer' or unset " +
                                    "'signBundle' or set 'signBundle' to false.");
                }
            }

            // hdiutil is always available so there's no need to test for availability.
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
