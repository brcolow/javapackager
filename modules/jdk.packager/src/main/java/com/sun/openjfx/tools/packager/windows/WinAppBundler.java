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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import com.sun.openjfx.tools.packager.JLinkBundlerHelper;
import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;
import com.sun.openjfx.tools.packager.AbstractImageBundler;
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
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ARGUMENTS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CLASSPATH;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_PROPERTIES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_JAR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PREFERENCES_ID;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PRELOADER_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.USER_JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.BIT_ARCH_64;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.BIT_ARCH_64_RUNTIME;
import static com.sun.openjfx.tools.packager.windows.WindowsBundlerParam.WIN_RUNTIME;

public class WinAppBundler extends AbstractImageBundler {

    public final static String WIN_BUNDLER_PREFIX = BUNDLER_PREFIX + "windows/";

    public WinAppBundler() {
        super();
        baseResourceLoader = WinResources.class;
    }

    public static final BundlerParamInfo<File> ICON_ICO = new StandardBundlerParam<>(
            ".ico Icon",
            "Icon for the application, in ICO format.",
            "icon.ico",
            File.class,
            params -> {
                File file = ICON.fetchFrom(params);
                if (file != null && !file.getName().toLowerCase().endsWith(".ico")) {
                    Log.info(MessageFormat.format("The specified icon \"{0}\" is not an ICO file and will not be " +
                            "used. The default icon will be used in it's place.", file));
                    return null;
                }
                return file;
            },
            (s, p) -> new File(s));

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) throw new ConfigException("Parameters map is null.",
                    "Pass in a non-null parameters map.");

            return doValidate(params);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    // to be used by chained bundlers, e.g. by EXE bundler to avoid
    // skipping validation if p.type does not include "image"
    boolean doValidate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.WINDOWS) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        RelativeFileSet runtime = WIN_RUNTIME.fetchFrom(p);
        /*
        if (runtime != null && !runtime.contains("legal/java.base/LICENSE")) {
            throw new ConfigException("The Java runtime specified (\"" + runtime + "\") does not seem to be" +
                    "correct: ", "Either do not explicitly set the runtime and use the default JAVA_HOME or else" +
                    "specify an actual Java runtime.");
        }
        */
        // Make sure that javapackager.exe exists.
        /*
        File tool = new File(System.getProperty("java.home") + "\\bin\\javapackager.exe");

        if (!tool.exists()) {
            throw new ConfigException("This copy of the JDK does not support Windows.",
                    "Please use the Oracle JDK for Windows.");
        }
        */

        // validate runtime bit-architecture
        testRuntimeBitArchitecture(p);

        return true;
    }

    private static void testRuntimeBitArchitecture(Map<String, ? super Object> params) throws ConfigException {
        if ("true".equalsIgnoreCase(System.getProperty("fxpackager.disableBitArchitectureMismatchCheck"))) {
            Log.debug("Disabled check for bit architecture mismatch.");
            return;
        }

        if ((BIT_ARCH_64.fetchFrom(params) != BIT_ARCH_64_RUNTIME.fetchFrom(params))) {
            throw new ConfigException("Bit architecture mismatch between FX SDK and JRE runtime.",
                    "Make sure to use JRE runtime with correct bit architecture.");
        }
    }

    //it is static for the sake of sharing with "Exe" bundles
    // that may skip calls to validate/bundle in this class!
    private static File getRootDir(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, APP_FS_NAME.fetchFrom(p));
    }

    public static String getLauncherName(Map<String, ? super Object> p) {
        return APP_FS_NAME.fetchFrom(p) + ".exe";
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app\\" + APP_FS_NAME.fetchFrom(p) +".cfg";
    }

    public boolean bundle(Map<String, ? super Object> p, File outputDirectory) {
        return doBundle(p, outputDirectory, false) != null;
    }

    File doBundle(Map<String, ? super Object> p, File outputDirectory, boolean dependentTask) {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outputDirectory.getAbsolutePath()));
        }
        if (!outputDirectory.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outputDirectory.getAbsolutePath()));
        }
        try {
            if (!dependentTask) {
                Log.info(MessageFormat.format("Creating app bundle: {0} in {1}", APP_NAME.fetchFrom(p),
                        outputDirectory.getAbsolutePath()));
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
                Log.info(MessageFormat.format("Result application bundle: {0}", outputDirectory.getAbsolutePath()));
            }

            return rootDirectory;
        } catch (IOException ex) {
            Log.info(ex.toString());
            Log.verbose(ex);
            return null;
        } catch (Exception ex) {
            Log.info("Exception: " + ex);
            Log.debug(ex);
            return null;
        }
    }

    private static final String RUNTIME_AUTO_DETECT = ".runtime.autodetect";

    public static void extractFlagsFromRuntime(Map<String, ? super Object> params) {
        if (params.containsKey(RUNTIME_AUTO_DETECT)) {
            return;
        }

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
        return "Windows Application Image";
    }

    @Override
    public String getDescription() {
        return "A Directory based image of a windows Application with an optionally co-bundled JRE. Used as a base " +
                "for the Installer bundlers";
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
        return Arrays.asList(APP_NAME,
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
                WIN_RUNTIME);
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }
}
