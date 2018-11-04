/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.COPYRIGHT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VENDOR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;

public class WindowsAppImageBuilder extends AbstractAppImageBuilder {

    private static final String WINDOWS_BUNDLER_PREFIX = BUNDLER_PREFIX + "windows" + File.separator;

    private static final String EXECUTABLE_NAME = "WinLauncher.exe";
    private static final String LIBRARY_NAME = "packager.dll";
    private static final String[] VS_VERS = {"100", "110", "120", "140"};
    private static final String REDIST_MSVCR = "vcruntimeVS_VER.dll";
    private static final String REDIST_MSVCP = "msvcpVS_VER.dll";
    private static final String TEMPLATE_APP_ICON = "packager/windows/javalogo_white_48.ico";
    private static final String EXECUTABLE_PROPERTIES_TEMPLATE = "packager/windows/WinLauncher.properties";

    private final Path root;
    private final Path appDir;
    private final Path runtimeDir;

    private final Map<String, ? super Object> params;

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

    public static final BundlerParamInfo<Boolean> REBRAND_EXECUTABLE = new WindowsBundlerParam<>(
            "Rebrand Launcher",
            "Update the launcher with the application icon and update ownership information.",
            "win.launcher.rebrand",
            Boolean.class,
        params -> Boolean.TRUE,
        (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<File> ICON_ICO = new StandardBundlerParam<>(
            ".ico Icon",
            "Icon for the application, in ICO format.",
            "icon.ico",
            File.class,
        params -> {
            File f = ICON.fetchFrom(params);
            if (f != null && !f.getName().toLowerCase().endsWith(".ico")) {
                Log.info(MessageFormat.format("The specified icon \"{0}\" is not an ICO file and will not be " +
                        "used.  The default icon will be used in it's place.", f));
                return null;
            }
            return f;
        },
        (s, p) -> new File(s));

    public WindowsAppImageBuilder(Map<String, Object> config, Path imageOutDir) throws IOException {
        super(config, imageOutDir.resolve(APP_FS_NAME.fetchFrom(config) + "/runtime"));

        Objects.requireNonNull(imageOutDir);

        this.params = config;
        this.root = imageOutDir.resolve(APP_FS_NAME.fetchFrom(params));
        this.appDir = root.resolve("app");
        this.runtimeDir = root.resolve("runtime");
        Files.createDirectories(appDir);
        Files.createDirectories(runtimeDir);
    }

    private static String getLauncherName(Map<String, ? super Object> p) {
        return APP_FS_NAME.fetchFrom(p) + ".exe";
    }

    private static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app/" + APP_FS_NAME.fetchFrom(p) + ".cfg";
    }

    private File getConfig_AppIcon(Map<String, ? super Object> params) {
        return new File(getConfigRoot(params), APP_FS_NAME.fetchFrom(params) + ".ico");
    }

    private File getConfig_ExecutableProperties(Map<String, ? super Object> params) {
        return new File(getConfigRoot(params), APP_FS_NAME.fetchFrom(params) + ".properties");
    }

    private File getConfigRoot(Map<String, ? super Object> params) {
        return CONFIG_ROOT.fetchFrom(params);
    }

    private void cleanupConfigFiles(Map<String, ? super Object> params) {
        getConfig_AppIcon(params).delete();
        getConfig_ExecutableProperties(params).delete();
    }

    @Override
    public void prepareApplicationFiles() {
        Map<String, ? super Object> originalParams = new HashMap<>(params);
        File rootFile = root.toFile();
        if (!rootFile.isDirectory() && !rootFile.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    rootFile.getAbsolutePath()));
        }
        if (!rootFile.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    rootFile.getAbsolutePath()));
        }
        try {
            // create the .exe launchers
            createLauncherForEntryPoint(params);

            // copy the jars
            copyApplication(params);

            // copy in the needed libraries
            Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows/" + LIBRARY_NAME),
                    root.resolve(LIBRARY_NAME));
            copyMSVCDLLs();

            // create the secondary launchers, if any
            List<Map<String, ? super Object>> entryPoints = StandardBundlerParam.SECONDARY_LAUNCHERS.fetchFrom(params);
            for (Map<String, ? super Object> entryPoint : entryPoints) {
                Map<String, ? super Object> tmp = new HashMap<>(originalParams);
                tmp.putAll(entryPoint);
                createLauncherForEntryPoint(tmp);
            }

        } catch (IOException ex) {
            Log.info("Exception: " + ex);
            Log.debug(ex);
        } finally {
            if (VERBOSE.fetchFrom(params)) {
                Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                        getConfigRoot(params).getAbsolutePath()));
            } else {
                cleanupConfigFiles(params);
            }
        }
    }

    private void copyMSVCDLLs() throws IOException {
        String vsVer = null;

        // first copy the ones needed for the launcher
        for (String thisVer : VS_VERS) {
            if (copyMSVCDLLs(thisVer)) {
                vsVer = thisVer;
                break;
            }
        }
        if (vsVer == null) {
            throw new RuntimeException("Could not find MSVC dlls");
        }

        AtomicReference<IOException> ioe = new AtomicReference<>();
        final String finalVsVer = vsVer;
        try (Stream<Path> files = Files.list(runtimeDir.resolve("bin"))) {
            files.filter(p -> Pattern.matches("^(vcruntime|msvcp|msvcr|ucrtbase|api-ms-win-).*\\.dll$",
                    p.toFile().getName().toLowerCase()))
                    .filter(p -> !p.toString().toLowerCase().endsWith(finalVsVer + ".dll"))
                    .forEach(p -> {
                        try {
                            Files.copy(p, root.resolve(p.toFile().getName()));
                        } catch (IOException e) {
                            ioe.set(e);
                        }
                    });
        }

        IOException e = ioe.get();
        if (e != null) {
            throw e;
        }
    }

    private boolean copyMSVCDLLs(String vsVer) throws IOException {
        Path msvcrPath = Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows/" +
                REDIST_MSVCR.replaceAll("VS_VER", vsVer));
        Path msvcpPath = Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows/" +
                REDIST_MSVCP.replaceAll("VS_VER", vsVer));

        if (Files.exists(msvcrPath) && Files.exists(msvcpPath)) {
            System.out.println("Copying to: " + root.resolve(REDIST_MSVCR.replaceAll("VS_VER", vsVer)));
            System.out.println("Copying to: " + root.resolve(REDIST_MSVCP.replaceAll("VS_VER", vsVer)));
            Files.copy(msvcrPath, root.resolve(REDIST_MSVCR.replaceAll("VS_VER", vsVer)));
            Files.copy(msvcpPath, root.resolve(REDIST_MSVCP.replaceAll("VS_VER", vsVer)));
            return true;
        }

        return false; // not found
    }

    private void validateValueAndPut(Map<String, String> data, String key,
                                     BundlerParamInfo<String> param, Map<String, ? super Object> params) {
        String value = param.fetchFrom(params);
        if (value.contains("\r") || value.contains("\n")) {
            Log.info("Configuration Parameter " + param.getID() + " contains multiple lines of text, ignore it");
            data.put(key, "");
            return;
        }
        data.put(key, value);
    }

    private void prepareExecutableProperties(Map<String, ? super Object> params)
            throws IOException {
        Map<String, String> data = new HashMap<>();

        // mapping Java parameters in strings for version resource
        data.put("COMMENTS", "");
        validateValueAndPut(data, "COMPANY_NAME", VENDOR, params);
        validateValueAndPut(data, "FILE_DESCRIPTION", DESCRIPTION, params);
        validateValueAndPut(data, "FILE_VERSION", VERSION, params);
        data.put("INTERNAL_NAME", getLauncherName(params));
        validateValueAndPut(data, "LEGAL_COPYRIGHT", COPYRIGHT, params);
        data.put("LEGAL_TRADEMARK", "");
        data.put("ORIGINAL_FILENAME", getLauncherName(params));
        data.put("PRIVATE_BUILD", "");
        validateValueAndPut(data, "PRODUCT_NAME", APP_NAME, params);
        validateValueAndPut(data, "PRODUCT_VERSION", VERSION, params);
        data.put("SPECIAL_BUILD", "");

        Writer w = new BufferedWriter(new FileWriter(getConfig_ExecutableProperties(params)));
        String content = preprocessTextResource(
                WINDOWS_BUNDLER_PREFIX + getConfig_ExecutableProperties(params).getName(),
                "Template for creating executable properties file.", EXECUTABLE_PROPERTIES_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();
    }

    private void createLauncherForEntryPoint(Map<String, ? super Object> p) throws IOException {

        File launcherIcon = ICON_ICO.fetchFrom(p);
        File icon = launcherIcon != null ? launcherIcon : ICON_ICO.fetchFrom(params);
        File iconTarget = getConfig_AppIcon(p);

        InputStream in = locateResource(APP_NAME.fetchFrom(params) + ".ico",
                "icon",
                TEMPLATE_APP_ICON,
                icon,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        Files.copy(in, iconTarget.toPath());

        writeCfgFile(p, root.resolve(getLauncherCfgName(p)).toFile(), "$APPDIR\\runtime");

        prepareExecutableProperties(p);

        // Copy executable root folder
        Path executableFile = root.resolve(getLauncherName(p));
        Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows/" + EXECUTABLE_NAME),
                executableFile);
        File launcher = executableFile.toFile();
        launcher.setWritable(true, true);

        Path tool = Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows",
                "javapackager.exe");
        tool.toFile().setWritable(true);
        tool.toFile().setExecutable(true);

        // Update branding of EXE file
        if (REBRAND_EXECUTABLE.fetchFrom(p)) {
            // Run tool on launcher file to change the icon and the metadata.
            try {
                if (WindowsDefender.isThereAPotentialWindowsDefenderIssue()) {
                    Log.info(MessageFormat.format("Warning: Windows Defender may prevent the Java Packager from " +
                            "functioning. If there is an issue, it can be addressed by either disabling realtime " +
                            "monitoring, or adding an exclusion for the directory \"{0}\".",
                            System.getProperty("java.io.tmpdir")));
                }

                launcher.setWritable(true);

                if (iconTarget.exists()) {
                    ProcessBuilder pb = new ProcessBuilder(
                            tool.toAbsolutePath().toString(),
                            "--icon-swap",
                            iconTarget.getAbsolutePath(),
                            launcher.getAbsolutePath());
                    IOUtils.exec(pb, VERBOSE.fetchFrom(p));
                }

                File executableProperties = getConfig_ExecutableProperties(p);

                if (executableProperties.exists()) {
                    ProcessBuilder pb = new ProcessBuilder(
                            tool.toAbsolutePath().toString(),
                            "--version-swap",
                            executableProperties.getAbsolutePath(),
                            launcher.getAbsolutePath());
                    IOUtils.exec(pb, VERBOSE.fetchFrom(p));
                }
            } finally {
                executableFile.toFile().setReadOnly();
            }
        }

        Files.copy(iconTarget.toPath(), root.resolve(APP_NAME.fetchFrom(p) + ".ico"));
    }

    private void copyApplication(Map<String, ? super Object> params) throws IOException {
        List<RelativeFileSet> appResourcesList = APP_RESOURCES_LIST.fetchFrom(params);
        if (appResourcesList == null) {
            throw new RuntimeException("Null app resources?");
        }
        for (RelativeFileSet appResources : appResourcesList) {
            if (appResources == null) {
                throw new RuntimeException("Null app resources?");
            }
            File srcdir = appResources.getBaseDirectory();
            for (String fname : appResources.getIncludedFiles()) {
                copyEntry(appDir, srcdir, fname);
            }
        }
    }

    @Override
    protected String getCacheLocation(Map<String, ? super Object> params) {
        return "$CACHEDIR/";
    }
}
