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
package com.sun.openjfx.tools.packager.linux;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ICON;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;

public class LinuxAppImageBuilder extends AbstractAppImageBuilder {

    private static final String EXECUTABLE_NAME = "JavaAppLauncher";
    private static final String LIBRARY_NAME = "libpackager.so";

    private final Path root;
    private final Path appDir;
    private final Path runtimeDir;
    private final Path resourcesDir;

    private final Map<String, ? super Object> params;

    public static final BundlerParamInfo<File> ICON_PNG = new StandardBundlerParam<>(
            ".png Icon",
            "Icon for the application, in PNG format.",
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.info(MessageFormat.format("The specified icon \"{0}\" is not a PNG file and will not be used." +
                            " The default icon will be used in it's place.", f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public LinuxAppImageBuilder(Map<String, Object> config, Path imageOutDir) throws IOException {
        super(config, imageOutDir.resolve(APP_FS_NAME.fetchFrom(config) + "/runtime"));

        Objects.requireNonNull(imageOutDir);

        this.root = imageOutDir.resolve(APP_FS_NAME.fetchFrom(config));
        this.appDir = root.resolve("app");
        this.runtimeDir = root.resolve("runtime");
        this.resourcesDir = root.resolve("resources");
        this.params = new HashMap<>();
        config.entrySet().stream().forEach(e -> params.put(e.getKey(), e.getValue()));
        Files.createDirectories(appDir);
        Files.createDirectories(runtimeDir);
        Files.createDirectories(resourcesDir);
    }

    // it is static for the sake of sharing with "installer" bundlers
    // that may skip calls to validate/bundle in this class!
    public static File getRootDir(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, APP_FS_NAME.fetchFrom(p));
    }

    private static String getLauncherName(Map<String, ? super Object> p) {
        return APP_FS_NAME.fetchFrom(p);
    }

    private static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app/" + APP_FS_NAME.fetchFrom(p) + ".cfg";
    }

    @Override
    public void prepareApplicationFiles() {
        Map<String, ? super Object> originalParams = new HashMap<>(params);

        try {
            // create the primary launcher
            createLauncherForEntryPoint(params, root);

            // Copy library to the launcher folder
            Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/linux/" + LIBRARY_NAME),
                    root.resolve(LIBRARY_NAME));

            // create the secondary launchers, if any
            List<Map<String, ? super Object>> entryPoints = StandardBundlerParam.SECONDARY_LAUNCHERS.fetchFrom(params);
            for (Map<String, ? super Object> entryPoint : entryPoints) {
                Map<String, ? super Object> tmp = new HashMap<>(originalParams);
                tmp.putAll(entryPoint);
                // remove name.fs that was calculated for main launcher.
                // otherwise, wrong launcher name will be selected.
                tmp.remove(APP_FS_NAME.getID());
                createLauncherForEntryPoint(tmp, root);
            }

            // Copy class path entries to Java folder
            copyApplication();

            // Copy icon to Resources folder
            copyIcon();

        } catch (IOException ex) {
            Log.info("Exception: " + ex);
            Log.debug(ex);
        }
    }

    private void createLauncherForEntryPoint(Map<String, ? super Object> p, Path rootDir) throws IOException {
        // Copy executable to Linux folder
        Path executableFile = root.resolve(getLauncherName(p));

        Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/linux/" + EXECUTABLE_NAME),
                executableFile);
        executableFile.toFile().setExecutable(true, false);
        executableFile.toFile().setWritable(true, true);

        writeCfgFile(p, root.resolve(getLauncherCfgName(p)).toFile(), "$APPDIR/runtime");
    }

    private void copyIcon() throws IOException {
        File icon = ICON_PNG.fetchFrom(params);
        if (icon != null) {
            File iconTarget = new File(resourcesDir.toFile(), APP_FS_NAME.fetchFrom(params) + ".png");
            IOUtils.copyFile(icon, iconTarget);
        }
    }

    private void copyApplication() throws IOException {
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
