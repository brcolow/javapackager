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

package com.openjdk.tools.packager.windows;

import com.openjdk.tools.packager.AbstractImageBundler;
import com.openjdk.tools.packager.BundlerParamInfo;
import com.openjdk.tools.packager.ConfigException;
import com.openjdk.tools.packager.IOUtils;
import com.openjdk.tools.packager.Log;
import com.openjdk.tools.packager.Platform;
import com.openjdk.tools.packager.RelativeFileSet;
import com.openjdk.tools.packager.StandardBundlerParam;
import com.openjdk.tools.packager.UnsupportedPlatformException;
import com.openjdk.legacy.builders.windows.WindowsAppImageBuilder;

import com.openjdk.legacy.JLinkBundlerHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.ResourceBundle;

import static com.openjdk.tools.packager.StandardBundlerParam.*;
import static com.openjdk.tools.packager.windows.WindowsBundlerParam.*;
import com.openjdk.legacy.builders.AbstractAppImageBuilder;

public class WinAppBundler extends AbstractImageBundler {

    private static final ResourceBundle I18N =
            ResourceBundle.getBundle(WinAppBundler.class.getName());

    public static final BundlerParamInfo<File> ICON_ICO = new StandardBundlerParam<>(
            I18N.getString("param.icon-ico.name"),
            I18N.getString("param.icon-ico.description"),
            "icon.ico",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".ico")) {
                    Log.info(MessageFormat.format(I18N.getString("message.icon-not-ico"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public WinAppBundler() {
        super();
        baseResourceLoader = WinResources.class;
    }

    public final static String WIN_BUNDLER_PREFIX =
            BUNDLER_PREFIX + "windows/";

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

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
    boolean doValidate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.WINDOWS) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        // Make sure that javapackager.exe exists.
        File tool = new File(System.getProperty("java.home") + "\\bin\\javapackager.exe");

        if (!tool.exists()) {
            throw new ConfigException(
                    I18N.getString("error.no-windows-resources"),
                    I18N.getString("error.no-windows-resources.advice"));
        }

        //validate runtime bit-architectire
        testRuntimeBitArchitecture(p);

        return true;
    }

    private static void testRuntimeBitArchitecture(Map<String, ? super Object> params) throws ConfigException {
        if ("true".equalsIgnoreCase(System.getProperty("fxpackager.disableBitArchitectureMismatchCheck"))) {
            Log.debug(I18N.getString("message.disable-bit-architecture-check"));
            return;
        }

        if ((BIT_ARCH_64.fetchFrom(params) != BIT_ARCH_64_RUNTIME.fetchFrom(params))) {
            throw new ConfigException(
                    I18N.getString("error.bit-architecture-mismatch"),
                    I18N.getString("error.bit-architecture-mismatch.advice"));
        }
    }

    //it is static for the sake of sharing with "Exe" bundles
    // that may skip calls to validate/bundle in this class!
    private static File getRootDir(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, APP_NAME.fetchFrom(p));
    }

    public static String getLauncherName(Map<String, ? super Object> p) {
        return APP_NAME.fetchFrom(p) +".exe";
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app\\" + APP_NAME.fetchFrom(p) +".cfg";
    }

    public boolean bundle(Map<String, ? super Object> p, File outputDirectory) {
        return doBundle(p, outputDirectory, false) != null;
    }

    File doBundle(Map<String, ? super Object> p, File outputDirectory, boolean dependentTask) {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new RuntimeException(MessageFormat.format(I18N.getString("error.cannot-create-output-dir"), outputDirectory.getAbsolutePath()));
        }
        if (!outputDirectory.canWrite()) {
            throw new RuntimeException(MessageFormat.format(I18N.getString("error.cannot-write-to-output-dir"), outputDirectory.getAbsolutePath()));
        }
        try {
            if (!dependentTask) {
                Log.info(MessageFormat.format(I18N.getString("message.creating-app-bundle"), APP_NAME.fetchFrom(p), outputDirectory.getAbsolutePath()));
            }

            // Create directory structure
            File rootDirectory = getRootDir(outputDirectory, p);
            IOUtils.deleteRecursive(rootDirectory);
            rootDirectory.mkdirs();

            if (!p.containsKey(JLinkBundlerHelper.JLINK_BUILDER.getID())) {
                p.put(JLinkBundlerHelper.JLINK_BUILDER.getID(), "windowsapp-image-builder");
            }

            AbstractAppImageBuilder appBuilder = new WindowsAppImageBuilder(p, outputDirectory.toPath());
            JLinkBundlerHelper.execute(p, appBuilder);

            if (!dependentTask) {
                Log.info(MessageFormat.format(I18N.getString("message.result-dir"), outputDirectory.getAbsolutePath()));
            }

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

    private static final String RUNTIME_AUTO_DETECT = ".runtime.autodetect";

    public static void extractFlagsFromRuntime(Map<String, ? super Object> params) {
        if (params.containsKey(".runtime.autodetect")) return;

        params.put(RUNTIME_AUTO_DETECT, "attempted");

        String commandline;
        File runtimePath = JLinkBundlerHelper.getJDKHome(params).toFile();
        File launcherPath = new File(runtimePath, "bin\\java.exe");

        ProcessBuilder pb = new ProcessBuilder(launcherPath.getAbsolutePath(), "-version");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (PrintStream pout = new PrintStream(baos)) {
                IOUtils.exec(pb, Log.isDebug(), true, pout);
            }

            commandline = baos.toString();
        } catch (IOException e) {
            e.printStackTrace();
            params.put(RUNTIME_AUTO_DETECT, "failed");
            return;
        }

        AbstractImageBundler.extractFlagsFromVersion(params, commandline);
        params.put(RUNTIME_AUTO_DETECT, "succeeded");
    }

    @Override
    public String getName() {
        return I18N.getString("bundler.name");
    }

    @Override
    public String getDescription() {
        return I18N.getString("bundler.description");
    }

    @Override
    public String getID() {
        return "windows.app";
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
                // APP_RESOURCES_LIST, // ??
                ARGUMENTS,
                CLASSPATH,
                ICON_ICO,
                JVM_OPTIONS,
                JVM_PROPERTIES,
                MAIN_CLASS,
                MAIN_JAR,
                PREFERENCES_ID,
                PRELOADER_CLASS,
                USER_JVM_OPTIONS,
                VERSION,
                WIN_RUNTIME
            );
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }
}
