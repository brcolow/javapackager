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

package com.sun.openjfx.tools.packager.windows;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;
import com.sun.openjfx.tools.packager.bundlers.BundleParams;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_CDS_CACHE_MODE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ENABLE_APP_CDS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SECONDARY_LAUNCHERS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SERVICE_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.APP_REGISTRY_NAME;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.BIT_ARCH_64;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.COPYRIGHT;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.DESCRIPTION;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.FA_CONTENT_TYPE;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.FA_DESCRIPTION;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.FA_EXTENSIONS;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.FA_ICON;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.FILE_ASSOCIATIONS;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.IDENTIFIER;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.INSTALLDIR_CHOOSER;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.INSTALLER_FILE_NAME;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.MENU_GROUP;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.MENU_HINT;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.RUN_AT_STARTUP;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.SHORTCUT_HINT;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.START_ON_INSTALL;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.STOP_ON_UNINSTALL;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.SYSTEM_WIDE;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.TITLE;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.VENDOR;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.VERSION;

public class WinExeBundler extends AbstractBundler {

    public WinExeBundler() {
        super();
    }

    public static final BundlerParamInfo<WinAppBundler> APP_BUNDLER = new WindowsBundlerParam<>(
            "",
            "",
            "win.app.bundler",
            WinAppBundler.class,
        params -> new WinAppBundler(), null);

    public static final BundlerParamInfo<WinServiceBundler> SERVICE_BUNDLER = new WindowsBundlerParam<>(
            "",
            "",
            "win.service.bundler",
            WinServiceBundler.class,
        params -> new WinServiceBundler(), null);

    public static final BundlerParamInfo<File> CONFIG_ROOT = new WindowsBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
        params -> {
            File imagesRoot = new File(BUILD_ROOT.fetchFrom(params), "windows");
            imagesRoot.mkdirs();
            return imagesRoot;
        },
        (s, p) -> null);

    // default for .exe is user level installation
    // only do system wide if explicitly requested
    public static final StandardBundlerParam<Boolean> EXE_SYSTEM_WIDE = new StandardBundlerParam<>(
            "System Wide",
            "Should this application attempt to install itself system wide, or only for each user?  Null " +
                    "means use the system default.",
            "win.exe." + BundleParams.PARAM_SYSTEM_WIDE,
            Boolean.class,
        params -> params.containsKey(SYSTEM_WIDE.getID()) ?
                SYSTEM_WIDE.fetchFrom(params) : false, // EXEs default to user local install
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null : Boolean.valueOf(s));

    public static final BundlerParamInfo<File> EXE_IMAGE_DIR = new WindowsBundlerParam<>(
            "",
            "",
            "win.exe.imageDir",
            File.class,
        params -> {
            File imagesRoot = IMAGES_ROOT.fetchFrom(params);
            if (!imagesRoot.exists()) {
                imagesRoot.mkdirs();
            }
            return new File(imagesRoot, "win-exe.image");
        },
        (s, p) -> null);

    private static final String DEFAULT_EXE_PROJECT_TEMPLATE = "packager/windows/template.iss";
    private static final String TOOL_INNO_SETUP_COMPILER = "iscc.exe";

    public static final BundlerParamInfo<String> TOOL_INNO_SETUP_COMPILER_EXECUTABLE = new WindowsBundlerParam<>(
            "InnoSetup iscc.exe location",
            "File path to iscc.exe from the InnoSetup tool.",
            "win.exe.iscc.exe",
            String.class,
        params -> {
            for (String dirString : (System.getenv("PATH") + ";C:\\Program Files (x86)\\Inno Setup 5;" +
                    "C:\\Program Files\\Inno Setup 5").split(";")) {
                File f = new File(dirString.replace("\"", ""), TOOL_INNO_SETUP_COMPILER);
                if (f.isFile()) {
                    return f.toString();
                }
            }
            return null;
        }, null);

    @Override
    public String getName() {
        return "Windows EXE Installer";
    }

    @Override
    public String getDescription() {
        return "Microsoft Windows EXE Installer, via InnoIDE.";
    }

    @Override
    public String getID() {
        return "exe";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(WinAppBundler.getAppBundleParameters());
        results.addAll(getExeBundleParameters());
        return results;
    }

    public static Collection<BundlerParamInfo<?>> getExeBundleParameters() {
        return Arrays.asList(DESCRIPTION,
                COPYRIGHT,
                LICENSE_FILE,
                MENU_GROUP,
                MENU_HINT,
                // RUN_AT_STARTUP,
                SHORTCUT_HINT,
                // SERVICE_HINT,
                // START_ON_INSTALL,
                // STOP_ON_UNINSTALL,
                SYSTEM_WIDE,
                TITLE,
                VENDOR,
                INSTALLDIR_CHOOSER);
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }

    static class VersionExtractor extends PrintStream {
        double version;

        VersionExtractor() {
            super(new ByteArrayOutputStream());
        }

        double getVersion() {
            if (version == 0f) {
                String content = new String(((ByteArrayOutputStream) out).toByteArray());
                Pattern pattern = Pattern.compile("Inno Setup (\\d+.?\\d*)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    String v = matcher.group(1);
                    version = Double.valueOf(v);
                }
            }
            return version;
        }
    }

    private static double findToolVersion(String toolName) {
        try {
            if (toolName == null || "".equals(toolName)) {
                return 0f;
            }

            ProcessBuilder pb = new ProcessBuilder(toolName, "/?");
            VersionExtractor ve = new VersionExtractor();
            IOUtils.exec(pb, Log.isDebug(), true, ve); // not interested in the output
            double version = ve.getVersion();
            Log.verbose(MessageFormat.format("Detected [{0}] version [{1}]", toolName, version));
            return version;
        } catch (Exception e) {
            if (Log.isDebug()) {
                e.printStackTrace();
            }
            return 0f;
        }
    }

    @Override
    public boolean validate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) {
                throw new ConfigException("Parameters map is null.", "Pass in a non-null parameters map.");
            }

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(p).validate(p);

            // make sure some key values don't have newlines
            for (BundlerParamInfo<String> pi : Arrays.asList(APP_NAME, COPYRIGHT, DESCRIPTION,
                    MENU_GROUP, TITLE, VENDOR, VERSION)) {
                String v = pi.fetchFrom(p);
                if (v.contains("\n") | v.contains("\r")) {
                    throw new ConfigException("Parameter '" + pi.getID() + "' cannot contain a newline.",
                            "Change the value of '" + pi.getID() + " so that it does not contain any newlines");
                }
            }

            // exe bundlers trim the copyright to 100 characters, tell them this will happen
            if (COPYRIGHT.fetchFrom(p).length() > 100) {
                throw new ConfigException("The copyright string is too long for InnoSetup.",
                        "Provide a copyright string shorter than 100 characters.");
            }

            // validate license file, if used, exists in the proper place
            if (p.containsKey(LICENSE_FILE.getID())) {
                List<RelativeFileSet> appResourcesList = APP_RESOURCES_LIST.fetchFrom(p);
                for (String license : LICENSE_FILE.fetchFrom(p)) {
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

            if (SERVICE_HINT.fetchFrom(p)) {
                SERVICE_BUNDLER.fetchFrom(p).validate(p);
            }

            double innoVersion = findToolVersion(TOOL_INNO_SETUP_COMPILER_EXECUTABLE.fetchFrom(p));

            // Inno Setup 5+ is required
            double minVersion = 5.0f;

            if (innoVersion < minVersion) {
                Log.info(MessageFormat.format("Detected [{0}] version {1} but version {2} is required.",
                        TOOL_INNO_SETUP_COMPILER, innoVersion, minVersion));
                throw new ConfigException("Can not find Inno Setup Compiler (iscc.exe).",
                        "Download Inno Setup 5 or later from http://www.jrsoftware.org and add it to the PATH.");
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

    private boolean prepareProto(Map<String, ? super Object> params) throws IOException {
        File imageDir = EXE_IMAGE_DIR.fetchFrom(params);
        File appOutputDir = APP_BUNDLER.fetchFrom(params).doBundle(params, imageDir, true);
        if (appOutputDir == null) {
            return false;
        }

        List<String> licenseFiles = LICENSE_FILE.fetchFrom(params);
        if (licenseFiles != null) {
            //need to copy license file to the root of win.app.image
            outerLoop:
            for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
                for (String s : licenseFiles) {
                    if (rfs.contains(s)) {
                        File lfile = new File(rfs.getBaseDirectory(), s);
                        IOUtils.copyFile(lfile, new File(imageDir, lfile.getName()));
                        break outerLoop;
                    }
                }
            }
        }

        for (Map<String, ? super Object> fileAssociation : FILE_ASSOCIATIONS.fetchFrom(params)) {
            File icon = FA_ICON.fetchFrom(fileAssociation); //TODO FA_ICON_ICO
            if (icon != null && icon.exists()) {
                IOUtils.copyFile(icon, new File(appOutputDir, icon.getName()));
            }
        }

        if (SERVICE_HINT.fetchFrom(params)) {
            // copies the service launcher to the app root folder
            appOutputDir = SERVICE_BUNDLER.fetchFrom(params).doBundle(params, appOutputDir, true);
            return appOutputDir != null;
        }
        return true;
    }

    public File bundle(Map<String, ? super Object> p, File outputDirectory) {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outputDirectory.getAbsolutePath()));
        }

        if (!outputDirectory.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outputDirectory.getAbsolutePath()));
        }

        if (WindowsDefender.isThereAPotentialWindowsDefenderIssue()) {
            Log.info(MessageFormat.format("Warning: Windows Defender may prevent the Java Packager from functioning. " +
                    "If there is an issue, it can be addressed by either disabling realtime monitoring, or adding an " +
                    "exclusion for the directory \"{0}\".", System.getProperty("java.io.tmpdir")));
        }

        // validate we have valid tools before continuing
        String iscc = TOOL_INNO_SETUP_COMPILER_EXECUTABLE.fetchFrom(p);
        if (iscc == null || !new File(iscc).isFile()) {
            Log.info("Can not find Inno Setup Compiler (iscc.exe).");
            Log.info(MessageFormat.format("InnoSetup compiler set to {0}", iscc));
            return null;
        }

        File imageDir = EXE_IMAGE_DIR.fetchFrom(p);
        try {
            imageDir.mkdirs();

            boolean menuShortcut = MENU_HINT.fetchFrom(p);
            boolean desktopShortcut = SHORTCUT_HINT.fetchFrom(p);
            if (!menuShortcut && !desktopShortcut) {
                //both can not be false - user will not find the app
                Log.verbose("At least one type of shortcut is required. Enabling menu shortcut.");
                p.put(MENU_HINT.getID(), true);
            }

            if (prepareProto(p) && prepareProjectConfig(p)) {
                File configScript = getConfig_Script(p);
                if (configScript.exists()) {
                    Log.info(MessageFormat.format("Running WSH script on application image [{0}]",
                            configScript.getAbsolutePath()));
                    IOUtils.run("wscript", configScript, VERBOSE.fetchFrom(p));
                }
                return buildEXE(p, outputDirectory);
            }
            return null;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } finally {
            try {
                if (VERBOSE.fetchFrom(p)) {
                    saveConfigFiles(p);
                }
                if (imageDir != null && !Log.isDebug()) {
                    IOUtils.deleteRecursive(imageDir);
                } else if (imageDir != null) {
                    Log.info(MessageFormat.format("Kept working directory for debug: {0}", imageDir.getAbsolutePath()));
                }
            } catch (IOException ex) {
                Log.debug(ex.getMessage());
                // noinspection ReturnInsideFinallyBlock
                return null;
            }
        }
    }

    // name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(EXE_IMAGE_DIR.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + "-post-image.wsf");
    }

    private void saveConfigFiles(Map<String, ? super Object> params) {
        try {
            File configRoot = CONFIG_ROOT.fetchFrom(params);
            if (getConfig_ExeProjectFile(params).exists()) {
                IOUtils.copyFile(getConfig_ExeProjectFile(params),
                        new File(configRoot, getConfig_ExeProjectFile(params).getName()));
            }
            if (getConfig_Script(params).exists()) {
                IOUtils.copyFile(getConfig_Script(params),
                        new File(configRoot, getConfig_Script(params).getName()));
            }
            if (getConfig_SmallInnoSetupIcon(params).exists()) {
                IOUtils.copyFile(getConfig_SmallInnoSetupIcon(params),
                        new File(configRoot, getConfig_SmallInnoSetupIcon(params).getName()));
            }
            Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                    configRoot.getAbsolutePath()));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private String getAppIdentifier(Map<String, ? super Object> params) {
        String nm = IDENTIFIER.fetchFrom(params);

        // limitation of innosetup
        if (nm.length() > 126) {
            nm = nm.substring(0, 126);
        }

        return nm;
    }


    private String getLicenseFile(Map<String, ? super Object> params) {
        List<String> licenseFiles = LICENSE_FILE.fetchFrom(params);
        if (licenseFiles == null || licenseFiles.isEmpty()) {
            return "";
        } else {
            return licenseFiles.get(0);
        }
    }

    private void validateValueAndPut(Map<String, String> data, String key, BundlerParamInfo<String> param,
                                     Map<String, ? super Object> params) throws IOException {
        String value = param.fetchFrom(params);
        if (value.contains("\r") || value.contains("\n")) {
            throw new IOException("Configuration Parameter " + param.getID() +
                    " cannot contain multiple lines of text");
        }
        data.put(key, innosetupEscape(value));
    }

    private String innosetupEscape(String value) {
        if (value.contains("\"") || !value.trim().equals(value)) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void prepareMainProjectFile(Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();
        data.put("PRODUCT_APP_IDENTIFIER", innosetupEscape(getAppIdentifier(params)));

        validateValueAndPut(data, "APPLICATION_NAME", APP_NAME, params);
        validateValueAndPut(data, "APPLICATION_FS_NAME", APP_FS_NAME, params);
        validateValueAndPut(data, "APPLICATION_VENDOR", VENDOR, params);
        validateValueAndPut(data, "APPLICATION_VERSION", VERSION, params);
        validateValueAndPut(data, "INSTALLER_FILE_NAME", INSTALLER_FILE_NAME, params);

        data.put("APPLICATION_LAUNCHER_FILENAME", innosetupEscape(WinAppBundler.getLauncherName(params)));

        data.put("APPLICATION_DESKTOP_SHORTCUT", SHORTCUT_HINT.fetchFrom(params) ? "returnTrue" : "returnFalse");
        data.put("APPLICATION_MENU_SHORTCUT", MENU_HINT.fetchFrom(params) ? "returnTrue" : "returnFalse");
        validateValueAndPut(data, "APPLICATION_GROUP", MENU_GROUP, params);
        validateValueAndPut(data, "APPLICATION_COMMENTS", TITLE, params); // TODO this seems strange, at least in name
        validateValueAndPut(data, "APPLICATION_COPYRIGHT", COPYRIGHT, params);

        data.put("APPLICATION_LICENSE_FILE", innosetupEscape(getLicenseFile(params)));
        data.put("DISABLE_DIR_PAGE", INSTALLDIR_CHOOSER.fetchFrom(params) ? "No" : "Yes");

        Boolean isSystemWide = EXE_SYSTEM_WIDE.fetchFrom(params);

        if (isSystemWide) {
            data.put("APPLICATION_INSTALL_ROOT", "{pf}");
            data.put("APPLICATION_INSTALL_PRIVILEGE", "admin");
        } else {
            data.put("APPLICATION_INSTALL_ROOT", "{localappdata}");
            data.put("APPLICATION_INSTALL_PRIVILEGE", "lowest");
        }

        if (BIT_ARCH_64.fetchFrom(params)) {
            data.put("ARCHITECTURE_BIT_MODE", "x64");
        } else {
            data.put("ARCHITECTURE_BIT_MODE", "");
        }

        if (SERVICE_HINT.fetchFrom(params)) {
            data.put("RUN_FILENAME", innosetupEscape(WinServiceBundler.getAppSvcName(params)));
        } else {
            validateValueAndPut(data, "RUN_FILENAME", APP_FS_NAME, params);
        }
        validateValueAndPut(data, "APPLICATION_DESCRIPTION", DESCRIPTION, params);
        data.put("APPLICATION_SERVICE", SERVICE_HINT.fetchFrom(params) ? "returnTrue" : "returnFalse");
        data.put("APPLICATION_NOT_SERVICE", SERVICE_HINT.fetchFrom(params) ? "returnFalse" : "returnTrue");
        data.put("APPLICATION_APP_CDS_INSTALL", ENABLE_APP_CDS.fetchFrom(params) &&
                ("install".equals(APP_CDS_CACHE_MODE.fetchFrom(params)) ||
                        "auto+install".equals(APP_CDS_CACHE_MODE.fetchFrom(params))) ? "returnTrue" : "returnFalse");
        data.put("START_ON_INSTALL", START_ON_INSTALL.fetchFrom(params) ? "-startOnInstall" : "");
        data.put("STOP_ON_UNINSTALL", STOP_ON_UNINSTALL.fetchFrom(params) ? "-stopOnUninstall" : "");
        data.put("RUN_AT_STARTUP", RUN_AT_STARTUP.fetchFrom(params) ? "-runAtStartup" : "");

        StringBuilder secondaryLaunchersCfg = new StringBuilder();
        for (Map<String, ? super Object> launcher : SECONDARY_LAUNCHERS.fetchFrom(params)) {
            String applicationName = APP_FS_NAME.fetchFrom(launcher);
            if (MENU_HINT.fetchFrom(launcher)) {
                // Name: "{group}\APPLICATION_NAME";
                // Filename: "{app}\APPLICATION_FS_NAME.exe";
                // IconFilename: "{app}\APPLICATION_NAME.ico"
                secondaryLaunchersCfg.append("Name: \"{group}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append("\"; Filename: \"{app}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append(".exe\"; IconFilename: \"{app}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append(".ico\"\r\n");
            }
            if (SHORTCUT_HINT.fetchFrom(launcher)) {
                // Name: "{commondesktop}\APPLICATION_NAME";
                // Filename: "{app}\APPLICATION_FS_NAME.exe";
                // IconFilename: "{app}\APPLICATION_NAME.ico"
                secondaryLaunchersCfg.append("Name: \"{commondesktop}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append("\"; Filename: \"{app}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append(".exe\";  IconFilename: \"{app}\\");
                secondaryLaunchersCfg.append(applicationName);
                secondaryLaunchersCfg.append(".ico\"\r\n");
            }
        }
        data.put("SECONDARY_LAUNCHERS", secondaryLaunchersCfg.toString());

        StringBuilder registryEntries = new StringBuilder();
        String regName = APP_REGISTRY_NAME.fetchFrom(params);
        List<Map<String, ? super Object>> fetchFrom = FILE_ASSOCIATIONS.fetchFrom(params);
        for (int i = 0; i < fetchFrom.size(); i++) {
            Map<String, ? super Object> fileAssociation = fetchFrom.get(i);
            String description = FA_DESCRIPTION.fetchFrom(fileAssociation);

            List<String> extensions = FA_EXTENSIONS.fetchFrom(fileAssociation);
            String entryName = regName + "File";
            if (i > 0) {
                entryName += "." + i;
            }

            if (extensions == null) {
                Log.info("Creating association with null extension.");
            } else {
                for (String ext : extensions) {
                    if (isSystemWide) {
                        // Root: HKCR;
                        // Subkey: \".myp\";
                        // ValueType: string; ValueName: \"\";
                        // ValueData: \"MyProgramFile\";
                        // Flags: uninsdeletevalue
                        registryEntries.append("Root: HKCR; Subkey: \".")
                                .append(ext)
                                .append("\"; ValueType: string; ValueName: \"\"; ValueData: \"")
                                .append(entryName)
                                .append("\"; Flags: uninsdeletevalue\r\n");
                    } else {
                        registryEntries.append("Root: HKCU; Subkey: \"Software\\Classes\\.")
                                .append(ext)
                                .append("\"; ValueType: string; ValueName: \"\"; ValueData: \"")
                                .append(entryName)
                                .append("\"; Flags: uninsdeletevalue\r\n");
                    }
                }
            }

            if (extensions != null && !extensions.isEmpty()) {
                String ext = extensions.get(0);
                List<String> mimeTypes = FA_CONTENT_TYPE.fetchFrom(fileAssociation);
                for (String mime : mimeTypes) {
                    if (isSystemWide) {
                        // Root: HKCR;
                        // Subkey: HKCR\\Mime\\Database\\Content Type\\application/chaos;
                        // ValueType: string;
                        // ValueName: Extension;
                        // ValueData: .chaos;
                        // Flags: uninsdeletevalue
                        registryEntries.append("Root: HKCR; Subkey: \"Mime\\Database\\Content Type\\")
                            .append(mime)
                            .append("\"; ValueType: string; ValueName: \"Extension\"; ValueData: \".")
                            .append(ext)
                            .append("\"; Flags: uninsdeletevalue\r\n");
                    } else {
                        registryEntries
                                .append("Root: HKCU; Subkey: \"Software\\Classes\\Mime\\Database\\Content Type\\")
                                .append(mime)
                                .append("\"; ValueType: string; ValueName: \"Extension\"; ValueData: \".")
                                .append(ext)
                                .append("\"; Flags: uninsdeletevalue\r\n");
                    }
                }
            }

            if (isSystemWide) {
                // Root: HKCR;
                // Subkey: \"MyProgramFile\";
                // ValueType: string;
                // ValueName: \"\";
                // ValueData: \"My Program File\";
                // Flags: uninsdeletekey
                registryEntries.append("Root: HKCR; Subkey: \"")
                    .append(entryName)
                    .append("\"; ValueType: string; ValueName: \"\"; ValueData: \"")
                    .append(description)
                    .append("\"; Flags: uninsdeletekey\r\n");
            } else {
                registryEntries.append("Root: HKCU; Subkey: \"Software\\Classes\\")
                    .append(entryName)
                    .append("\"; ValueType: string; ValueName: \"\"; ValueData: \"")
                    .append(description)
                    .append("\"; Flags: uninsdeletekey\r\n");

            }

            File icon = FA_ICON.fetchFrom(fileAssociation); // TODO FA_ICON_ICO
            if (icon != null && icon.exists()) {
                if (isSystemWide) {
                    // Root: HKCR;
                    // Subkey: \"MyProgramFile\\DefaultIcon\";
                    // ValueType: string;
                    // ValueName: \"\";
                    // ValueData: \"{app}\\MYPROG.EXE,0\"\n"
                    registryEntries.append("Root: HKCR; Subkey: \"")
                        .append(entryName)
                        .append("\\DefaultIcon\"; ValueType: string; ValueName: \"\"; ValueData: \"{app}\\")
                        .append(icon.getName())
                        .append("\"\r\n");
                } else {
                    registryEntries.append("Root: HKCU; Subkey: \"Software\\Classes\\")
                            .append(entryName)
                            .append("\\DefaultIcon\"; ValueType: string; ValueName: \"\"; ValueData: \"{app}\\")
                            .append(icon.getName())
                            .append("\"\r\n");
                }
            }

            if (isSystemWide) {
                // Root: HKCR;
                // Subkey: \"MyProgramFile\\shell\\open\\command\";
                // ValueType: string;
                // ValueName: \"\";
                // ValueData: \"\"\"{app}\\MYPROG.EXE\"\" \"\"%1\"\"\"\n
                registryEntries.append("Root: HKCR; Subkey: \"")
                        .append(entryName)
                        .append("\\shell\\open\\command\"; ValueType: string; " +
                                "ValueName: \"\"; ValueData: \"\"\"{app}\\")
                        .append(APP_FS_NAME.fetchFrom(params))
                        .append("\"\" \"\"%1\"\"\"\r\n");
            } else {
                registryEntries.append("Root: HKCU; Subkey: \"Software\\Classes\\")
                        .append(entryName)
                        .append("\\shell\\open\\command\"; ValueType: string; " +
                                "ValueName: \"\"; ValueData: \"\"\"{app}\\")
                        .append(APP_FS_NAME.fetchFrom(params))
                        .append("\"\" \"\"%1\"\"\"\r\n");
            }
        }
        if (registryEntries.length() > 0) {
            data.put("FILE_ASSOCIATIONS", "ChangesAssociations=yes\r\n\r\n[Registry]\r\n" + registryEntries.toString());
        } else {
            data.put("FILE_ASSOCIATIONS", "");
        }

        Writer w = new BufferedWriter(new FileWriter(getConfig_ExeProjectFile(params)));
        String content = preprocessTextResource(
                WinAppBundler.WIN_BUNDLER_PREFIX + getConfig_ExeProjectFile(params).getName(),
                "Inno Setup project file", DEFAULT_EXE_PROJECT_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();
    }

    private static final String DEFAULT_INNO_SETUP_ICON = "packager/windows/icon_inno_setup.bmp";

    private boolean prepareProjectConfig(Map<String, ? super Object> params) throws IOException {
        prepareMainProjectFile(params);

        //prepare installer icon
        File iconTarget = getConfig_SmallInnoSetupIcon(params);
        fetchResource(WinAppBundler.WIN_BUNDLER_PREFIX + iconTarget.getName(),
                "setup dialog icon",
                DEFAULT_INNO_SETUP_ICON,
                iconTarget,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));

        fetchResource(WinAppBundler.WIN_BUNDLER_PREFIX + getConfig_Script(params).getName(),
                "script to run after application image is populated",
                (String) null,
                getConfig_Script(params),
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        return true;
    }

    private File getConfig_SmallInnoSetupIcon(Map<String, ? super Object> params) {
        return new File(EXE_IMAGE_DIR.fetchFrom(params),
                APP_FS_NAME.fetchFrom(params) + "-setup-icon.bmp");
    }

    private File getConfig_ExeProjectFile(Map<String, ? super Object> params) {
        return new File(EXE_IMAGE_DIR.fetchFrom(params),
                APP_FS_NAME.fetchFrom(params) + ".iss");
    }

    private File buildEXE(Map<String, ? super Object> params, File outdir) throws IOException {
        Log.verbose(MessageFormat.format("Generating EXE for installer to: {0}", outdir.getAbsolutePath()));

        outdir.mkdirs();

        // run candle
        ProcessBuilder pb = new ProcessBuilder(TOOL_INNO_SETUP_COMPILER_EXECUTABLE.fetchFrom(params),
                "/o" + outdir.getAbsolutePath(), getConfig_ExeProjectFile(params).exists() ?
                getConfig_ExeProjectFile(params).getAbsolutePath() :
                Paths.get("./src/main/resources/com/sun/openjfx/tools/ " + DEFAULT_EXE_PROJECT_TEMPLATE)
                        .toAbsolutePath().toString());
        pb.directory(EXE_IMAGE_DIR.fetchFrom(params));
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));

        Log.info(MessageFormat.format("Installer (.exe) saved to: {0}", outdir.getAbsolutePath()));

        // presume the result is the ".exe" file with the newest modified time
        // not the best solution, but it is the most reliable
        File result = null;
        long lastModified = 0;
        File[] list = outdir.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.getName().endsWith(".exe") && f.lastModified() > lastModified) {
                    result = f;
                    lastModified = f.lastModified();
                }
            }
        }

        return result;
    }
}
