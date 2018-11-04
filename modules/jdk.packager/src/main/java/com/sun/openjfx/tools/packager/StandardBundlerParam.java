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

package com.sun.openjfx.tools.packager;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.openjfx.tools.packager.bundlers.BundleParams;

public class StandardBundlerParam<T> extends BundlerParamInfo<T> {

    private static final String JAVABASEJMOD = "java.base.jmod";
    private static final String MANIFEST_PRELOADER = "JavaFX-Preloader-Class";
    public static final String MANIFEST_JAVAFX_MAIN = "JavaFX-Application-Class";

    public StandardBundlerParam(String name, String description, String id,
                                Class<T> valueType,
                                Function<Map<String, ? super Object>, T> defaultValueFunction,
                                BiFunction<String, Map<String, ? super Object>, T> stringConverter) {
        this.name = name;
        this.description = description;
        this.id = id;
        this.valueType = valueType;
        this.defaultValueFunction = defaultValueFunction;
        this.stringConverter = stringConverter;
    }

    public static final StandardBundlerParam<RelativeFileSet> APP_RESOURCES = new StandardBundlerParam<>(
            "Resources",
            "All of the files to place in the resources directory.  Including all needed jars as assets.",
            BundleParams.PARAM_APP_RESOURCES,
            RelativeFileSet.class,
            null,
            null);

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<RelativeFileSet>> APP_RESOURCES_LIST = new StandardBundlerParam<>(
            "Resources List",
            "A List of RelativeFileSet objects containing all of the files to place in the resources directory. " +
                    "Including all needed jars as assets.",
            BundleParams.PARAM_APP_RESOURCES + "List",
            (Class<List<RelativeFileSet>>) (Object) List.class,
        p -> new ArrayList<>(Collections.singletonList(APP_RESOURCES.fetchFrom(p))),
        // Default is appResources, as a single item list
        (s, map) -> createAppResourcesListFromString(s));

    public static final StandardBundlerParam<String> SOURCE_DIR = new StandardBundlerParam<>(
            "Source Directory",
            "Path to the directory containing the files to be bundled.",
            "srcdir",
            String.class,
        p -> null,
        (s, p) -> {
            String value = String.valueOf(s);
            if (value.charAt(value.length() - 1) == File.separatorChar) {
                return value.substring(0, value.length() - 1);
            } else {
                return value;
            }
        });

    // note that each bundler is likely to replace this one with their own converter
    public static final StandardBundlerParam<RelativeFileSet> MAIN_JAR = new StandardBundlerParam<>(
            "Main Jar",
            "The main jar of the application.  This jar should have the main-class, and is relative to the " +
                    "assembled application dir.",
            "mainJar",
            RelativeFileSet.class,
        params -> {
            extractMainClassInfoFromAppResources(params);
            return (RelativeFileSet) params.get("mainJar");
        }, StandardBundlerParam::getMainJar);

    public static final StandardBundlerParam<String> CLASSPATH = new StandardBundlerParam<>(
            "Main Jar Classpath",
            "The classpath from the main jar of the application, relative to the assembled application directory.",
            "classpath",
            String.class,
        params -> {
            extractMainClassInfoFromAppResources(params);
            String cp = (String) params.get("classpath");
            return cp == null ? "" : cp;
        },
        (s, p) -> s.replace(File.pathSeparator, " "));

    public static final StandardBundlerParam<String> MAIN_CLASS = new StandardBundlerParam<>(
            "Main Class",
            "The main class for the application.  Either a javafx.application.Application instance or a java class " +
                    "with a main method.",
            BundleParams.PARAM_APPLICATION_CLASS,
            String.class,
        params -> {
            extractMainClassInfoFromAppResources(params);
            String s = (String) params.get(BundleParams.PARAM_APPLICATION_CLASS);
            if (s == null) {
                s = JLinkBundlerHelper.getMainClass(params);
            }
            return s;
        },
        (s, p) -> s);

    public static final StandardBundlerParam<String> APP_NAME = new StandardBundlerParam<>(
            "App Name",
            "The name of the application.",
            BundleParams.PARAM_NAME,
            String.class,
        params -> {
            String s = MAIN_CLASS.fetchFrom(params);
            if (s == null) {
                return null;
            }

            int idx = s.lastIndexOf(".");
            if (idx >= 0) {
                return s.substring(idx + 1);
            }
            return s;
        },
        (s, p) -> s);

    // keep out invalid/undesirable filename characters
    private static Pattern TO_FS_NAME = Pattern.compile("\\s|[\\\\/?:*<>|]");

    public static final StandardBundlerParam<String> APP_FS_NAME = new StandardBundlerParam<>(
            "App File System Name",
            "The name of the application suitable for file system use.  Typically this is just letters, numbers, " +
                    "dots, and dashes.",
            "name.fs",
            String.class,
        params -> TO_FS_NAME.matcher(APP_NAME.fetchFrom(params)).replaceAll(""),
        (s, p) -> s);

    public static final StandardBundlerParam<File> ICON = new StandardBundlerParam<>(
            "Icon",
            "The main icon of the application bundle.",
            BundleParams.PARAM_ICON,
            File.class,
        params -> null,
        (s, p) -> new File(s));

    public static final StandardBundlerParam<String> VENDOR = new StandardBundlerParam<>(
            "Vendor",
            "The vendor of the application.",
            BundleParams.PARAM_VENDOR,
            String.class,
        params -> "Unknown",
        (s, p) -> s);

    public static final StandardBundlerParam<String> CATEGORY = new StandardBundlerParam<>(
            "Category",
            "The category oor group of the application.  Generally speaking you will also want to specify " +
                    "application specific categories as well.",
            BundleParams.PARAM_CATEGORY,
            String.class,
        params -> "Unknown",
        (s, p) -> s);

    public static final StandardBundlerParam<String> DESCRIPTION = new StandardBundlerParam<>(
            "Description",
            "A longer description of the application",
            BundleParams.PARAM_DESCRIPTION,
            String.class,
        params -> params.containsKey(APP_NAME.getID()) ? APP_NAME.fetchFrom(params) : "none",
        (s, p) -> s);

    public static final StandardBundlerParam<String> COPYRIGHT = new StandardBundlerParam<>(
            "Copyright",
            "The copyright for the application.",
            BundleParams.PARAM_COPYRIGHT,
            String.class,
        params -> MessageFormat.format("Copyright (C) {0,date,YYYY}", new Date()),
        (s, p) -> s);

    public static final StandardBundlerParam<Boolean> USE_FX_PACKAGING = new StandardBundlerParam<>(
            "FX Packaging",
            "Should we use the JavaFX packaging conventions?",
            "fxPackaging",
            Boolean.class,
        params -> {
            extractMainClassInfoFromAppResources(params);
            Boolean result = (Boolean) params.get("fxPackaging");
            return (result == null) ? Boolean.FALSE : result;
        },
        (s, p) -> Boolean.valueOf(s));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> ARGUMENTS = new StandardBundlerParam<>(
            "Command Line Arguments",
            "Command Line Arguments to be passed to the main class if no arguments are specified by the launcher.",
            "arguments",
            (Class<List<String>>) (Object) List.class,
        params -> Collections.emptyList(),
        (s, p) -> splitStringWithEscapes(s));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> JVM_OPTIONS = new StandardBundlerParam<>(
            "JVM Options",
            "JVM flags and options to be passed in.",
            "jvmOptions",
            (Class<List<String>>) (Object) List.class,
        params -> Collections.emptyList(),
        (s, p) -> Arrays.asList(s.split("\\s+")));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<Map<String, String>> JVM_PROPERTIES = new StandardBundlerParam<>(
            "JVM System Properties",
            "JVM System Properties (of the -Dname=value variety).",
            "jvmProperties",
            (Class<Map<String, String>>) (Object) Map.class,
        params -> Collections.emptyMap(),
        (s, params) -> {
            Map<String, String> map = new HashMap<>();
            try {
                Properties p = new Properties();
                p.load(new StringReader(s));
                for (Map.Entry<Object, Object> entry : p.entrySet()) {
                    map.put((String) entry.getKey(), (String) entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return map;
        });

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<Map<String, String>> USER_JVM_OPTIONS = new StandardBundlerParam<>(
            "User JVM Options",
            "JVM Options the user may override, along with their default values.",
            "userJvmOptions",
            (Class<Map<String, String>>) (Object) Map.class,
        params -> Collections.emptyMap(),
        (s, params) -> {
            Map<String, String> map = new HashMap<>();
            try {
                Properties p = new Properties();
                p.load(new StringReader(s));
                for (Map.Entry<Object, Object> entry : p.entrySet()) {
                    map.put((String) entry.getKey(), (String) entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return map;
        });

    public static final StandardBundlerParam<String> TITLE = new StandardBundlerParam<>(
            "Title",
            "A title for the application.", //?? but what does it do?
            BundleParams.PARAM_TITLE,
            String.class,
            APP_NAME::fetchFrom, (s, p) -> s);

    // note that each bundler is likely to replace this one with their own converter
    public static final StandardBundlerParam<String> VERSION = new StandardBundlerParam<>(
            "Version",
            "The version of this application.",
            BundleParams.PARAM_VERSION,
            String.class,
        params -> "1.0", (s, p) -> s);

    public static final StandardBundlerParam<Boolean> SYSTEM_WIDE = new StandardBundlerParam<>(
            "System Wide",
            "Should this application attempt to install itself system wide, or only for each user?  Null means use " +
                    "the system default.",
            BundleParams.PARAM_SYSTEM_WIDE,
            Boolean.class,
        params -> null,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> SERVICE_HINT  = new StandardBundlerParam<>(
            "Service Hint",
            "The bundler should register the application as service/daemon.",
            BundleParams.PARAM_SERVICE_HINT,
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> START_ON_INSTALL  = new StandardBundlerParam<>(
            "Start On Install",
            "Controls whether the service/daemon should be started on install.",
            "startOnInstall",
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> STOP_ON_UNINSTALL = new StandardBundlerParam<>(
            "Stop On Uninstall",
            "Controls whether the service/daemon should be stopped on uninstall.",
            "stopOnUninstall",
            Boolean.class,
        params -> true,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> RUN_AT_STARTUP = new StandardBundlerParam<>(
            "Run At Startup",
            "Controls whether the service/daemon should be started during system startup.",
            "runAtStartup",
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> SIGN_BUNDLE = new StandardBundlerParam<>(
            "Sign Bundle",
            "If the bundler supports signing, request that the bundle be signed. Default value varies between " +
                    "bundlers. Bundlers that do not support signing will silently ignore this setting.",
            "signBundle",
            Boolean.class,
        params -> null,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> SHORTCUT_HINT = new StandardBundlerParam<>(
            "Shortcut Hint",
            "If the bundler can create desktop shortcuts, should it make one?",
            BundleParams.PARAM_SHORTCUT,
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final StandardBundlerParam<Boolean> MENU_HINT = new StandardBundlerParam<>(
            "Menu Hint",
            "If the bundler can add the application to the system menu, should it?",
            BundleParams.PARAM_MENU,
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> LICENSE_FILE = new StandardBundlerParam<>(
            "License",
            "The license file, relative to the assembled application directory.",
            BundleParams.PARAM_LICENSE_FILE,
            (Class<List<String>>) (Object) List.class,
        params -> Collections.emptyList(),
        (s, p) -> Arrays.asList(s.split(",")));

    public static final BundlerParamInfo<String> LICENSE_TYPE = new StandardBundlerParam<>(
            "",
            "",
            BundleParams.PARAM_LICENSE_TYPE,
            String.class,
        params -> "Unknown",
        (s, p) -> s);

    public static final StandardBundlerParam<File> BUILD_ROOT = new StandardBundlerParam<>(
            "Build Root",
            "The directory in which to use and place temporary files.",
            "buildRoot",
            File.class,
        params -> {
            try {
                return Files.createTempDirectory("fxbundler").toFile();
            } catch (IOException ioe) {
                return null;
            }
        },
        (s, p) -> new File(s));

    public static final StandardBundlerParam<String> IDENTIFIER = new StandardBundlerParam<>(
            "Identifier",
            "What is the machine readable identifier of this application?  The format should be a DNS name in " +
                    "reverse order, such as com.example.myapplication.",
            BundleParams.PARAM_IDENTIFIER,
            String.class,
        params -> {
            String s = MAIN_CLASS.fetchFrom(params);
            if (s == null) {
                return null;
            }

            int idx = s.lastIndexOf(".");
            if (idx >= 1) {
                return s.substring(0, idx);
            }
            return s;
        },
        (s, p) -> s);

    public static final StandardBundlerParam<String> PREFERENCES_ID = new StandardBundlerParam<>(
            "Preferences ID",
            "The preferences node to search for User JVM Options.  The format be a slash delimited version of the " +
                    "main package name, such as \"com/example/myapplication\".",
            "preferencesID",
            String.class,
        p -> Optional.ofNullable(IDENTIFIER.fetchFrom(p)).orElse("").replace('.', '/'),
        (s, p) -> s);

    public static final StandardBundlerParam<String> PRELOADER_CLASS = new StandardBundlerParam<>(
            "JavaFX Preloader Class Name",
            "For JavaFX applications only, this is the Fully Qualified Class Name of the preloader class. This " +
                    "class needs to exist in the classpath, preferably early in the path.",
            "preloader",
            String.class,
        p -> null, null);

    public static final StandardBundlerParam<Boolean> VERBOSE  = new StandardBundlerParam<>(
            "Verbose",
            "Flag to print out more information and saves configuration files for bundlers.",
            "verbose",
            Boolean.class,
        params -> false,
        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    public static final StandardBundlerParam<File> DROP_IN_RESOURCES_ROOT = new StandardBundlerParam<>(
            "Drop-In Resources Root",
            "The directory to look for bundler specific drop in resources.  If not set the classpath will be searched.",
            "dropinResourcesRoot",
            File.class,
        params -> new File("."),
        (s, p) -> new File(s));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<Map<String, ? super Object>>> SECONDARY_LAUNCHERS =
            new StandardBundlerParam<>(
                    "Secondary Launchers",
                    "A collection of bundle param info for secondary launchers",
                    "secondaryLaunchers",
                    (Class<List<Map<String, ? super Object>>>) (Object) List.class,
                params -> new ArrayList<>(1), (s, p) -> null);

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<Map<String, ? super Object>>> FILE_ASSOCIATIONS =
            new StandardBundlerParam<>(
                    "File Associations",
                    "A list of maps where each map describes a file association.  Uses the \"fileAssociation.\"" +
                            "series of bundle arguments in each map.",
                    "fileAssociations",
                    (Class<List<Map<String, ? super Object>>>) (Object) List.class,
                params -> new ArrayList<>(1), (s, p) -> null);

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> FA_EXTENSIONS = new StandardBundlerParam<>(
            "File Association Extension",
            "The File Extension to be associated, just the extension no dots.",
            "fileAssociation.extension",
            (Class<List<String>>) (Object) List.class,
        params -> null, // null means not matched to an extension
        (s, p) -> Arrays.asList(s.split("(,|\\s)+")));

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> FA_CONTENT_TYPE = new StandardBundlerParam<>(
            "File Association Content Type",
            "Content Type to be associated.  Such as application/x-vnd.my-awesome-app.",
            "fileAssociation.contentType",
            (Class<List<String>>) (Object) List.class,
        params -> null, // null means not matched to a content/mime type
        (s, p) -> Arrays.asList(s.split("(,|\\s)+")));

    public static final StandardBundlerParam<String> FA_DESCRIPTION = new StandardBundlerParam<>(
            "File Association Description",
            "The description to be used for associated files.  The default is \"<appName> File\".",
            "fileAssociation.description",
            String.class,
        params -> APP_NAME.fetchFrom(params) + " File", null);

    public static final StandardBundlerParam<File> FA_ICON = new StandardBundlerParam<>(
            "File Association Icon",
            "The Icon to be used for associated files.  Defaults to the application icon.",
            "fileAssociation.icon",
            File.class,
        ICON::fetchFrom, (s, p) -> new File(s));

    public static final StandardBundlerParam<Boolean> ENABLE_APP_CDS = new StandardBundlerParam<>(
            "Enable AppCDS",
            "Enabled and package with Application Class Data Sharing, including generation of .jsa file.",
            "commercial.AppCDS",
            Boolean.class,
        p -> false,
        (s, p) -> Boolean.parseBoolean(s));

    public static final StandardBundlerParam<String> APP_CDS_CACHE_MODE = new StandardBundlerParam<>(
            "AppCDS Cache Mode",
            "The mode in which the AppCDS .jpa files are generated and cached.  Current values are 'install', " +
                    "'auto', and 'auto+install'.",
            "commercial.AppCDS.cache",
            String.class,
        p -> "auto", (s, p) -> s);

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> APP_CDS_CLASS_ROOTS = new StandardBundlerParam<>(
            "AppCDS Root Classes",
            "List of \"root classes\" for AppCDS to generate class sharing data from.  Default is the main class.",
            "commercial.AppCDS.classRoots",
            (Class<List<String>>) ((Object) List.class),
        p -> Collections.singletonList(MAIN_CLASS.fetchFrom(p)),
        (s, p) -> Arrays.asList(s.split("[ ,:]")));

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<List<Path>> MODULE_PATH = new StandardBundlerParam<>(
            "Module Path",
            "When packaging the Java Runtime, this is the path JLink will look in for modules.",
            "module-path",
            (Class<List<Path>>) (Object) List.class,
        p -> getDefaultModulePath(),
        (s, p) -> {
            List<Path> modulePath = Arrays.stream(s.split(File.pathSeparator))
                    .map(ss -> new File(ss).toPath())
                    .collect(Collectors.toList());
            Path javaBasePath = JLinkBundlerHelper.findPathOfModule(modulePath, JAVABASEJMOD);

            // Add the default JDK module path to the module path.
            if (javaBasePath == null) {
                List<Path> jdkModulePath = getDefaultModulePath();

                if (jdkModulePath != null) {
                    modulePath.addAll(jdkModulePath);
                    javaBasePath = JLinkBundlerHelper.findPathOfModule(modulePath, JAVABASEJMOD);
                }
            }

            if (javaBasePath == null || !Files.exists(javaBasePath)) {
                Log.info("Warning: No JDK Modules found.");
            }

            return modulePath;
        });

    public static final BundlerParamInfo<String> MODULE = new StandardBundlerParam<>(
            "Main Module",
            "The main module of the application.  This module should have the main-class, and is on the module path.",
            "module",
            String.class,
        p -> null, (s, p) -> String.valueOf(s));

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<Set<String>> ADD_MODULES = new StandardBundlerParam<>(
            "Add Modules",
            "List of Modules to add to JImage creation, including possible services.",
            "add-modules",
            (Class<Set<String>>) (Object) Set.class,
        p -> new LinkedHashSet<>(Collections.singleton("java.base")),
        (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(","))));

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<Set<String>> LIMIT_MODULES = new StandardBundlerParam<>(
            "Limit Modules",
            "Modules to Limit JImage creation to.",
            "limit-modules",
            (Class<Set<String>>) (Object) Set.class,
        p -> new LinkedHashSet<>(),
        (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(","))));

    public static final BundlerParamInfo<Boolean> STRIP_NATIVE_COMMANDS = new StandardBundlerParam<>(
            "Strip Native Executables",
            "Removes native executables from the JImage creation.",
            "strip-native-commands",
            Boolean.class,
        p -> Boolean.TRUE, (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<Boolean> SINGLETON = new StandardBundlerParam<>(
            "Singleton",
            "Prevents from launching multiple instances of application.",
            BundleParams.PARAM_SINGLETON,
            Boolean.class,
        params -> Boolean.FALSE, (s, p) -> Boolean.valueOf(s));

    private static void extractMainClassInfoFromAppResources(Map<String, ? super Object> params) {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasPreloader = params.containsKey(PRELOADER_CLASS.getID());
        boolean hasModule = params.containsKey(MODULE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath || hasModule) {
            return;
        }

        // it's a pair.  The [0] is the srcdir [1] is the file relative to sourcedir
        List<String[]> filesToCheck = new ArrayList<>();

        if (hasMainJar) {
            RelativeFileSet rfs = MAIN_JAR.fetchFrom(params);
            for (String s : rfs.getIncludedFiles()) {
                filesToCheck.add(new String[]{rfs.getBaseDirectory().toString(), s});
            }
        } else if (hasMainJarClassPath) {
            for (String s : CLASSPATH.fetchFrom(params).split("\\s+")) {
                if (APP_RESOURCES.fetchFrom(params) != null) {
                    filesToCheck.add(new String[] {APP_RESOURCES.fetchFrom(params).getBaseDirectory().toString(), s});
                }
            }
        } else {
            List<RelativeFileSet> rfsl = APP_RESOURCES_LIST.fetchFrom(params);
            if (rfsl == null || rfsl.isEmpty()) {
                return;
            }
            for (RelativeFileSet rfs : rfsl) {
                if (rfs == null) {
                    continue;
                }

                for (String s : rfs.getIncludedFiles()) {
                    filesToCheck.add(new String[]{rfs.getBaseDirectory().toString(), s});
                }
            }
        }

        String declaredMainClass = (String) params.get(MAIN_CLASS.getID());

        // presume the set iterates in-order
        for (String[] fnames : filesToCheck) {
            try {
                // only sniff jars
                if (!fnames[1].toLowerCase().endsWith(".jar")) {
                    continue;
                }

                File file = new File(fnames[0], fnames[1]);
                // that actually exist
                if (!file.exists()) {
                    continue;
                }

                try (JarFile jf = new JarFile(file)) {
                    Manifest m = jf.getManifest();
                    Attributes attrs = (m != null) ? m.getMainAttributes() : null;

                    if (attrs != null) {
                        String mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
                        String fxMain = attrs.getValue(MANIFEST_JAVAFX_MAIN);
                        String preloaderClass = attrs.getValue(MANIFEST_PRELOADER);
                        if (hasMainClass) {
                            if (declaredMainClass.equals(fxMain)) {
                                params.put(USE_FX_PACKAGING.getID(), true);
                            } else if (declaredMainClass.equals(mainClass)) {
                                params.put(USE_FX_PACKAGING.getID(), false);
                            } else {
                                if (fxMain != null) {
                                    Log.info(MessageFormat.format(
                                            "The jar {0} has an FX Application class{1} that does not match the " +
                                                    "declared main {2}", fnames[1], fxMain, declaredMainClass));
                                }
                                if (mainClass != null) {
                                    Log.info(MessageFormat.format(
                                            "The jar {0} has a main class {1} that does not match the declared " +
                                                    "main {2}", fnames[1], mainClass, declaredMainClass));
                                }
                                continue;
                            }
                        } else {
                            if (fxMain != null) {
                                params.put(USE_FX_PACKAGING.getID(), true);
                                params.put(MAIN_CLASS.getID(), fxMain);
                            } else if (mainClass != null) {
                                params.put(USE_FX_PACKAGING.getID(), false);
                                params.put(MAIN_CLASS.getID(), mainClass);
                            } else {
                                continue;
                            }
                        }
                        if (!hasPreloader && preloaderClass != null) {
                            params.put(PRELOADER_CLASS.getID(), preloaderClass);
                        }
                        if (!hasMainJar) {
                            if (fnames[0] == null) {
                                fnames[0] = file.getParentFile().toString();
                            }
                            params.put(MAIN_JAR.getID(), new RelativeFileSet(new File(fnames[0]),
                                    new LinkedHashSet<>(Collections.singletonList(file))));
                        }
                        if (!hasMainJarClassPath) {
                            String cp = attrs.getValue(Attributes.Name.CLASS_PATH);
                            params.put(CLASSPATH.getID(), cp == null ? "" : cp);
                        }
                        break;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void validateMainClassInfoFromAppResources(Map<String, ? super Object> params)
            throws ConfigException {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasModule = params.containsKey(MODULE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath || hasModule) {
            return;
        }

        extractMainClassInfoFromAppResources(params);

        if (!params.containsKey(MAIN_CLASS.getID())) {
            if (hasMainJar) {
                throw new ConfigException(MessageFormat.format(
                        "An application class was not specified nor was one found in the jar {0}",
                        MAIN_JAR.fetchFrom(params)),
                        MessageFormat.format("Please specify a applicationClass or ensure that the jar {0} specifies " +
                                        "one in the manifest.", MAIN_JAR.fetchFrom(params)));
            } else if (hasMainJarClassPath) {
                throw new ConfigException(
                        "An application class was not specified nor was one found in the supplied classpath",
                        "Please specify a applicationClass or ensure that the classpath has a jar containing one " +
                                "in the manifest.");
            } else {
                throw new ConfigException(
                        "An application class was not specified nor was one found in the supplied " +
                                "application resources",
                        "Please specify a applicationClass or ensure that the appResources has a jar containing " +
                                "one in the manifest.");
            }
        }
    }


    private static List<String> splitStringWithEscapes(String s) {
        List<String> l = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : s.toCharArray()) {
            if ('"' == c) {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                l.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        l.add(current.toString());
        return l;
    }

    private static List<RelativeFileSet> createAppResourcesListFromString(
            String s) {
        List<RelativeFileSet> result = new ArrayList<>();
        for (String path : s.split("[:;]")) {
            File f = new File(path);
            if (f.getName().equals("*") || path.endsWith("/") || path.endsWith("\\")) {
                if (f.getName().equals("*")) {
                    f = f.getParentFile();
                }
                Set<File> theFiles = new HashSet<>();
                try {
                    Files.walk(f.toPath())
                            .filter(Files::isRegularFile)
                            .forEach(p -> theFiles.add(p.toFile()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                result.add(new RelativeFileSet(f, theFiles));
            } else {
                result.add(new RelativeFileSet(f.getParentFile(), Collections.singleton(f)));
            }
        }
        return result;
    }

    private static RelativeFileSet getMainJar(String moduleName, Map<String, ? super Object> params) {
        for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
            File appResourcesRoot = rfs.getBaseDirectory();
            File mainJarFile = new File(appResourcesRoot, moduleName);

            if (mainJarFile.exists()) {
                return new RelativeFileSet(appResourcesRoot, new LinkedHashSet<>(
                        Collections.singletonList(mainJarFile)));
            } else {
                List<Path> modulePath = MODULE_PATH.fetchFrom(params);
                Path modularJarPath = JLinkBundlerHelper.findPathOfModule(modulePath, moduleName);

                if (modularJarPath != null && Files.exists(modularJarPath)) {
                    return new RelativeFileSet(appResourcesRoot, new LinkedHashSet<>(
                            Collections.singletonList(modularJarPath.toFile())));
                }
            }
        }

        throw new IllegalArgumentException(new ConfigException(MessageFormat.format(
                "The configured main jar does not exist {0}", moduleName),
                "The main jar must be specified relative to the app resources (not an absolute path), and " +
                        "must exist within those resources."));
    }

    private static List<Path> getDefaultModulePath() {
        List<Path> result = new ArrayList<>();
        Path jdkModulePath = Paths.get(System.getProperty("java.home"), "jmods").toAbsolutePath();

        if (jdkModulePath != null && Files.exists(jdkModulePath)) {
            result.add(jdkModulePath);
        } else {
            // On a developer build the JDK Home isn't where we expect it
            // relative to the jmods directory. Do some extra
            // processing to find it.
            Map<String, String> env = System.getenv();

            if (env.containsKey("JDK_HOME")) {
                jdkModulePath = Paths.get(env.get("JDK_HOME"), ".." + File.separator + "images" +
                        File.separator + "jmods").toAbsolutePath();

                if (jdkModulePath != null && Files.exists(jdkModulePath)) {
                    result.add(jdkModulePath);
                }
            }
        }

        return result;
    }
}
