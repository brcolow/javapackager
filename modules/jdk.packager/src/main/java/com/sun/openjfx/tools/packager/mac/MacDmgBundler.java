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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SERVICE_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SYSTEM_WIDE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;

public class MacDmgBundler extends MacBaseInstallerBundler {

    static final String DEFAULT_BACKGROUND_IMAGE = "/packager/mac/background_dmg.png";
    static final String DEFAULT_DMG_SETUP_SCRIPT = "/packager/mac/DMGsetup.scpt";
    static final String TEMPLATE_BUNDLE_ICON =     "/packager/mac/GenericApp.icns";
    private static final String HDIUTIL = "/usr/bin/hdiutil";

    //existing SQE tests look for "license" string in the filenames
    // when they look for unauthorized license files in the build artifacts
    // Use different name to make them happy
    static final String DEFAULT_LICENSE_PLIST = "/packager/mac/lic_template.plist";

    public MacDmgBundler() {
        super();
    }

    public static final BundlerParamInfo<Boolean> SIMPLE_DMG = new StandardBundlerParam<>(
            "Simple DMG Generation",
            "Generate a DMG without AppleScript customizations.  Recommended for continuous automated builds.",
            "mac.dmg.simple",
            Boolean.class,
        params -> Boolean.FALSE,
        (s, p) -> Boolean.parseBoolean(s));

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX = new StandardBundlerParam<>(
            "Installer Suffix",
            "The suffix for the installer name for this package.  <name><suffix>.dmg.",
            "mac.dmg.installerName.suffix",
            String.class,
        params -> "",
        (s, p) -> s);

    public File bundle(Map<String, ? super Object> params, File outdir) {
        Log.info(MessageFormat.format("Building DMG package for {0}", APP_NAME.fetchFrom(params)));
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outdir.getAbsolutePath()));
        }

        File appImageDir = APP_IMAGE_BUILD_ROOT.fetchFrom(params);
        try {
            appImageDir.mkdirs();

            if (prepareAppBundle(params) != null && prepareConfigFiles(params)) {
                File configScript = getConfig_Script(params);
                if (configScript.exists()) {
                    Log.info(MessageFormat.format("Running shell script on application image [{0}]",
                            configScript.getAbsolutePath()));
                    IOUtils.run("bash", configScript, VERBOSE.fetchFrom(params));
                }

                return buildDMG(params, outdir);
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
                    //cleanup
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

    // remove
    private void cleanupConfigFiles(Map<String, ? super Object> params) {
        if (getConfig_VolumeBackground(params) != null) {
            getConfig_VolumeBackground(params).delete();
        }
        if (getConfig_VolumeIcon(params) != null) {
            getConfig_VolumeIcon(params).delete();
        }
        if (getConfig_VolumeScript(params) != null) {
            getConfig_VolumeScript(params).delete();
        }
        if (getConfig_Script(params) != null) {
            getConfig_Script(params).delete();
        }
        if (getConfig_LicenseFile(params) != null) {
            getConfig_LicenseFile(params).delete();
        }
        APP_BUNDLER.fetchFrom(params).cleanupConfigFiles(params);
    }

    private void prepareDMGSetupScript(String volumeName, Map<String, ? super Object> p) throws IOException {
        File dmgSetup = getConfig_VolumeScript(p);
        Log.verbose(MessageFormat.format("Preparing dmg setup: {0}", dmgSetup.getAbsolutePath()));

        // prepare config for exe
        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ACTUAL_VOLUME_NAME", volumeName);
        data.put("DEPLOY_APPLICATION_NAME", APP_NAME.fetchFrom(p));

        // treat default null as "system wide install"
        boolean systemWide = SYSTEM_WIDE.fetchFrom(p) == null || SYSTEM_WIDE.fetchFrom(p);

        if (systemWide) {
            data.put("DEPLOY_INSTALL_LOCATION", "POSIX file \"/Applications\"");
            data.put("DEPLOY_INSTALL_NAME", "Applications");
        } else {
            data.put("DEPLOY_INSTALL_LOCATION", "(path to desktop folder)");
            data.put("DEPLOY_INSTALL_NAME", "Desktop");
        }

        Writer w = new BufferedWriter(new FileWriter(dmgSetup));
        w.write(preprocessTextResource(MacAppBundler.MAC_BUNDLER_PREFIX + dmgSetup.getName(),
                "DMG setup script", DEFAULT_DMG_SETUP_SCRIPT, data, VERBOSE.fetchFrom(p),
                DROP_IN_RESOURCES_ROOT.fetchFrom(p)));
        w.close();
    }

    private File getConfig_VolumeScript(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-dmg-setup.scpt");
    }

    private File getConfig_VolumeBackground(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-background.png");
    }

    private File getConfig_VolumeIcon(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-volume.icns");
    }

    private File getConfig_LicenseFile(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-license.plist");
    }

    private void prepareLicense(Map<String, ? super Object> params) {
        try {
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

            if (licFile == null) {
                // this is NPE protection, validate should have caught it's absence
                // so we don't complain or throw an error
                return;
            }

            byte[] licenseContentOriginal = IOUtils.readFully(licFile);
            String licenseInBase64 = Base64.getEncoder().encodeToString(licenseContentOriginal);

            Map<String, String> data = new HashMap<>();
            data.put("APPLICATION_LICENSE_TEXT", licenseInBase64);

            Writer w = new BufferedWriter(new FileWriter(getConfig_LicenseFile(params)));
            w.write(preprocessTextResource(MacAppBundler.MAC_BUNDLER_PREFIX + getConfig_LicenseFile(params).getName(),
                    "License setup", DEFAULT_LICENSE_PLIST, data, VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params)));
            w.close();

        } catch (IOException ex) {
            Log.verbose(ex);
        }

    }

    private boolean prepareConfigFiles(Map<String, ? super Object> params) throws IOException {
        File bgTarget = getConfig_VolumeBackground(params);
        fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + bgTarget.getName(),
                "dmg background",
                DEFAULT_BACKGROUND_IMAGE,
                bgTarget,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));

        File iconTarget = getConfig_VolumeIcon(params);
        if (MacAppBundler.ICON_ICNS.fetchFrom(params) == null || !MacAppBundler.ICON_ICNS.fetchFrom(params).exists()) {
            fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + iconTarget.getName(),
                    "volume icon",
                    TEMPLATE_BUNDLE_ICON,
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        } else {
            fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + iconTarget.getName(),
                    "volume icon",
                    MacAppBundler.ICON_ICNS.fetchFrom(params),
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        }


        fetchResource(MacAppBundler.MAC_BUNDLER_PREFIX + getConfig_Script(params).getName(),
                "script to run after application image is populated",
                (String) null,
                getConfig_Script(params),
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));

        prepareLicense(params);

        // In theory we need to extract name from results of attach command
        // However, this will be a problem for customization as name will
        // possibly change every time and developer will not be able to fix it
        // As we are using tmp dir chance we get "different" name are low =>
        // Use fixed name we used for bundle
        prepareDMGSetupScript(APP_FS_NAME.fetchFrom(params), params);

        return true;
    }

    //name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-post-image.sh");
    }

    //Location of SetFile utility may be different depending on MacOS version
    // We look for several known places and if none of them work will
    // try ot find it
    private String findSetFileUtility() {
        String[] typicalPaths = {"/Developer/Tools/SetFile", "/usr/bin/SetFile", "/Developer/usr/bin/SetFile"};

        for (String path : typicalPaths) {
            File f = new File(path);
            if (f.exists() && f.canExecute()) {
                return path;
            }
        }

        // generic find attempt
        try {
            ProcessBuilder pb = new ProcessBuilder("xcrun", "-find", "SetFile");
            Process p = pb.start();
            InputStreamReader isr = new InputStreamReader(p.getInputStream());
            BufferedReader br = new BufferedReader(isr);
            String lineRead = br.readLine();
            if (lineRead != null) {
                File f = new File(lineRead);
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        } catch (IOException ignored) {

        }

        return null;
    }

    private File buildDMG(Map<String, ? super Object> p, File outdir) throws IOException {
        File imagesRoot = IMAGES_ROOT.fetchFrom(p);
        if (!imagesRoot.exists()) {
            imagesRoot.mkdirs();
        }

        File protoDmg = new File(imagesRoot, APP_FS_NAME.fetchFrom(p) + "-tmp.dmg");
        File finalDmg = new File(outdir, INSTALLER_NAME.fetchFrom(p) +
                INSTALLER_SUFFIX.fetchFrom(p) + ".dmg");

        File srcFolder = APP_IMAGE_BUILD_ROOT.fetchFrom(p); //new File(imageDir, p.name+".app");
        File predefinedImage = getPredefinedImage(p);
        if (predefinedImage != null) {
            srcFolder = predefinedImage;
        }

        Log.verbose(MessageFormat.format("Creating DMG file: {0}", finalDmg.getAbsolutePath()));

        protoDmg.delete();
        if (finalDmg.exists() && !finalDmg.delete()) {
            throw new IOException(MessageFormat.format("Dmg file exists ({0} and can not be removed.",
                    finalDmg.getAbsolutePath()));
        }

        protoDmg.getParentFile().mkdirs();
        finalDmg.getParentFile().mkdirs();

        String hdiUtilVerbosityFlag = Log.isDebug() ? "-verbose" : "-quiet";

        // create temp image
        System.out.println("HDIUTIL srcfolder: " + srcFolder.getAbsolutePath());
        System.out.println("-ov: " + protoDmg.getAbsolutePath());
        ScheduledExecutorService scheduler = null;
        if (System.getenv("TRAVIS") != null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                () -> System.out.println("Dont stall the Travis build!"), 9, 9, TimeUnit.MINUTES);
        }
        ProcessBuilder pb = new ProcessBuilder(HDIUTIL,
                "create",
                protoDmg.getAbsolutePath(),
                "-srcfolder", srcFolder.getAbsolutePath(),
                "-volname", APP_FS_NAME.fetchFrom(p),
                "-ov",
                "-fs", "HFS+J",
                "-format", "UDRW");
        IOUtils.exec(pb, VERBOSE.fetchFrom(p), true);

        if (scheduler != null) {
            scheduler.shutdown();
        }
        System.out.println("does dmg exist? " + protoDmg.exists());
        System.out.println("protoDmg: " + protoDmg.getAbsolutePath());
        System.out.println("imagesRoot: " + imagesRoot.getAbsolutePath());
        System.out.println("imagesRoot exists: " + imagesRoot.exists());

        // mount temp image
        pb = new ProcessBuilder(HDIUTIL,
                "attach",
                protoDmg.getAbsolutePath(),
                "-mountroot", imagesRoot.getAbsolutePath(),
                "-noverify");
        IOUtils.exec(pb, VERBOSE.fetchFrom(p), true);

        File mountedRoot = new File(imagesRoot.getAbsolutePath(), APP_FS_NAME.fetchFrom(p));

        // volume icon
        File volumeIconFile = new File(mountedRoot, ".VolumeIcon.icns");
        IOUtils.copyFile(getConfig_VolumeIcon(p), volumeIconFile);

        if (!SIMPLE_DMG.fetchFrom(p)) {
            // background image
            File bgdir = new File(mountedRoot, ".background");
            bgdir.mkdirs();
            IOUtils.copyFile(getConfig_VolumeBackground(p), new File(bgdir, "background.png"));

            pb = new ProcessBuilder("osascript", getConfig_VolumeScript(p).getAbsolutePath());
            IOUtils.exec(pb, VERBOSE.fetchFrom(p));
        }

        // Indicate that we want a custom icon
        // NB: attributes of the root directory are ignored when creating the volume
        // Therefore we have to do this after we mount image
        String setFileUtility = findSetFileUtility();
        if (setFileUtility != null) { //can not find utility => keep going without icon
            try {
                volumeIconFile.setWritable(true);
                // The "creator" attribute on a file is a legacy attribute
                // but it seems Finder excepts these bytes to be "icnC" for the volume icon
                // (http://endrift.com/blog/2010/06/14/dmg-files-volume-icons-cli/)
                // (might not work on Mac 10.13 with old XCode)
                pb = new ProcessBuilder(setFileUtility, "-c", "icnC", volumeIconFile.getAbsolutePath());
                IOUtils.exec(pb, VERBOSE.fetchFrom(p));
                volumeIconFile.setReadOnly();

                pb = new ProcessBuilder(setFileUtility, "-a", "C", mountedRoot.getAbsolutePath());
                IOUtils.exec(pb, VERBOSE.fetchFrom(p));
            } catch (IOException ex) {
                Log.info(ex.getMessage());
                Log.verbose("Cannot enable custom icon using SetFile utility");
            }
        } else {
            Log.verbose("Skip enabling custom icon as SetFile utility is not found");
        }

        // Detach the temporary image
        pb = new ProcessBuilder(HDIUTIL,
                "detach",
                mountedRoot.getAbsolutePath());
        IOUtils.exec(pb, VERBOSE.fetchFrom(p));

        // Compress it to a new image
        pb = new ProcessBuilder(HDIUTIL,
                "convert",
                protoDmg.getAbsolutePath(),
                "-format", "UDZO",
                "-o", finalDmg.getAbsolutePath());
        IOUtils.exec(pb, VERBOSE.fetchFrom(p));

        // add license if needed
        if (getConfig_LicenseFile(p).exists()) {
            // HDIUTIL unflatten your_image_file.dmg
            pb = new ProcessBuilder(HDIUTIL,
                    "unflatten",
                    finalDmg.getAbsolutePath());
            IOUtils.exec(pb, VERBOSE.fetchFrom(p));

            // add license
            pb = new ProcessBuilder(HDIUTIL,
                    "udifrez",
                    finalDmg.getAbsolutePath(),
                    "-xml",
                    getConfig_LicenseFile(p).getAbsolutePath());
            IOUtils.exec(pb, VERBOSE.fetchFrom(p));

            // HDIUTIL flatten your_image_file.dmg
            pb = new ProcessBuilder(HDIUTIL,
                    "flatten",
                    finalDmg.getAbsolutePath());
            IOUtils.exec(pb, VERBOSE.fetchFrom(p));
        }

        // Delete the temporary image
        protoDmg.delete();

        Log.info(MessageFormat.format("Result DMG installer for {0}: {1}",
                APP_NAME.fetchFrom(p), finalDmg.getAbsolutePath()));

        return finalDmg;
    }

    @Override
    public String getName() {
        return "Mac DMG Installer";
    }

    @Override
    public String getDescription() {
        return "Mac DMG Installer Bundle";
    }

    @Override
    public String getID() {
        return "dmg";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(getDMGBundleParameters());
        return results;
    }

    private Collection<BundlerParamInfo<?>> getDMGBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(Arrays.asList(INSTALLER_SUFFIX, LICENSE_FILE,
                SIMPLE_DMG, SYSTEM_WIDE));
        return results;
    }


    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) {
                throw new ConfigException("Parameters map is null.",
                        "Pass in a non-null parameters map.");
            }

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            // HDIUTIL is always available so there's no need to test for availability.
            if (SERVICE_HINT.fetchFrom(params)) {
                throw new ConfigException("DMG bundler doesn't support services.",
                        "Make sure that the service hint is set to false.");
            }

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
                                                "and that it is relative file reference.", license));
                    }
                }
            }

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
