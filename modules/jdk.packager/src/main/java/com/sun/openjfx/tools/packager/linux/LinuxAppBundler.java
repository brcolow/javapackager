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

package com.sun.openjfx.tools.packager.linux;

import com.sun.openjfx.tools.packager.AbstractImageBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.JreUtils;
import com.sun.openjfx.tools.packager.JreUtils.Rule;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;
import com.sun.openjfx.tools.packager.bundlers.BundleParams;

import com.sun.openjfx.tools.packager.JLinkBundlerHelper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;
import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;

public class LinuxAppBundler extends AbstractImageBundler {

    protected static final String LINUX_BUNDLER_PREFIX =
            BUNDLER_PREFIX + "linux" + File.separator;
    private static final String EXECUTABLE_NAME = "JavaAppLauncher";

    public static final BundlerParamInfo<File> ICON_PNG = new StandardBundlerParam<>(
            ".png Icon",
            "Icon for the application, in PNG format.",
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.info(MessageFormat.format("The specified icon \"{0}\" is not a PNG file and will not be used.  The default icon will be used in it's place.", f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<URL> RAW_EXECUTABLE_URL = new StandardBundlerParam<>(
            "Launcher URL",
            "Override the packager default launcher with a custom launcher.",
            "linux.launcher.url",
            URL.class,
            params -> {
                try {
                    return Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/linux/" + EXECUTABLE_NAME).toUri().toURL();
                } catch (MalformedURLException e) {
                    Log.info(e.toString());
                    return null;
                }
            },
            (s, p) -> {
                try {
                    return new URL(s);
                } catch (MalformedURLException e) {
                    Log.info(e.toString());
                    return null;
                }
            });

    //Subsetting of JRE is restricted.
    //JRE README defines what is allowed to strip:
    //   http://www.oracle.com/technetwork/java/javase/jre-8-readme-2095710.html
    //
    public static final BundlerParamInfo<Rule[]> LINUX_JRE_RULES = new StandardBundlerParam<>(
            "",
            "",
            ".linux.runtime.rules",
            Rule[].class,
            params -> new Rule[]{
                    Rule.prefixNeg("/bin"),
                    Rule.prefixNeg("/plugin"),
                    //Rule.prefixNeg("/lib/ext"), //need some of jars there for https to work
                    Rule.suffix("deploy.jar"), //take deploy.jar
                    Rule.prefixNeg("/lib/deploy"),
                    Rule.prefixNeg("/lib/desktop"),
                    Rule.substrNeg("libnpjp2.so")
            },
            (s, p) ->  null
    );

    public static final BundlerParamInfo<RelativeFileSet> LINUX_RUNTIME = new StandardBundlerParam<>(
            "JRE",
            "The Java Runtime to co-bundle. The default value is the current JRE running the bundler. A value of " +
                    "null will cause no JRE to be co-bundled and the system JRE will be used to launch the application.",
            BundleParams.PARAM_RUNTIME,
            RelativeFileSet.class,
            params -> JreUtils.extractJreAsRelativeFileSet(System.getProperty("java.home"),
                    LINUX_JRE_RULES.fetchFrom(params)),
            (s, p) -> JreUtils.extractJreAsRelativeFileSet(s, LINUX_JRE_RULES.fetchFrom(p))
    );

    @Override
    public boolean validate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) throw new ConfigException("Parameters map is null.",
                    "Pass in a non-null parameters map.");
            return doValidate(p);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    // used by chained bundlers to reuse validation logic
    boolean doValidate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.LINUX) {
            throw new UnsupportedPlatformException();
        }

        RelativeFileSet runtime = LINUX_RUNTIME.fetchFrom(p);
        if (runtime != null && !runtime.contains("legal/java.base/LICENSE")) {
            throw new ConfigException("The Java runtime specified (\"" + runtime + "\") does not seem to be" +
                    "correct: ", "Either do not explicitly set the runtime and use the default JAVA_HOME or else" +
                    "specify an actual Java runtime.");
        }

        imageBundleValidation(p);

        /*
        if (RAW_EXECUTABLE_URL.fetchFrom(p) == null) {
            throw new ConfigException(
                    "Java Packager does not support Linux.",
                    "Please use the Java Packager that ships with Oracle JDK for Linux.");
        }
        */
        return true;
    }

    // it is static for the sake of sharing with "installer" bundlers
    // that may skip calls to validate/bundle in this class!
    public static File getRootDir(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, APP_FS_NAME.fetchFrom(p));
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app/" + APP_FS_NAME.fetchFrom(p) + ".cfg";
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
            File rootDirectory = getRootDir(outputDirectory, p);
            IOUtils.deleteRecursive(rootDirectory);
            rootDirectory.mkdirs();

            if (!dependentTask) {
                Log.info(MessageFormat.format("Creating app bundle: {0}", rootDirectory.getAbsolutePath()));
            }

            if (!p.containsKey(JLinkBundlerHelper.JLINK_BUILDER.getID())) {
                p.put(JLinkBundlerHelper.JLINK_BUILDER.getID(), "linuxapp-image-builder");
            }

            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(p, outputDirectory.toPath());
            JLinkBundlerHelper.execute(p, appBuilder);

            return rootDirectory;
        } catch (IOException ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        } catch (Exception ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    @Override
    public String getName() {
        return "Linux Application Image";
    }

    @Override
    public String getDescription() {
        return "A Directory based image of a linux Application with an optionally co-bundled JRE.  Used as a base for the Installer bundlers.";
    }

    @Override
    public String getID() {
        return "linux.app";
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
                // ADD_MODULES,
                APP_NAME,
                APP_RESOURCES,
                // APP_RESOURCES_LIST, // ??
                ARGUMENTS,
                CLASSPATH,
                JVM_OPTIONS,
                JVM_PROPERTIES,
                LINUX_RUNTIME,
                MAIN_CLASS,
                MAIN_JAR,
                MODULE,
                PREFERENCES_ID,
                PRELOADER_CLASS,
                USER_JVM_OPTIONS,
                VERSION
        );
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }
}
