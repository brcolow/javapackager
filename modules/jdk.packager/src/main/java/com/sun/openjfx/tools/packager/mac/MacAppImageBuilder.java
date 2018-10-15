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
package com.sun.openjfx.tools.packager.mac;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ARGUMENTS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CATEGORY;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CLASSPATH;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.COPYRIGHT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_CONTENT_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_EXTENSIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FILE_ASSOCIATIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.IDENTIFIER;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_PROPERTIES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_JAR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PREFERENCES_ID;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PRELOADER_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SIGN_BUNDLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.USER_JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;
import static com.sun.openjfx.tools.packager.mac.MacAppBundler.BUNDLE_ID_SIGNING_PREFIX;
import static com.sun.openjfx.tools.packager.mac.MacAppBundler.DEVELOPER_ID_APP_SIGNING_KEY;
import static com.sun.openjfx.tools.packager.mac.MacBaseInstallerBundler.SIGNING_KEYCHAIN;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.sun.openjfx.tools.packager.AbstractAppImageBuilder;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;

public class MacAppImageBuilder extends AbstractAppImageBuilder {

    private static final String EXECUTABLE_NAME = "JavaAppLauncher";
    private static final String LIBRARY_NAME = "libpackager.dylib";
    private static final String TEMPLATE_BUNDLE_ICON = "/packager/mac/GenericApp.icns";
    private static final String OS_TYPE_CODE = "APPL";
    private static final String TEMPLATE_INFO_PLIST_LITE = "/packager/mac/Info-lite.plist.template";
    private static final String TEMPLATE_RUNTIME_INFO_PLIST = "/packager/mac/Runtime-Info.plist.template";

    private final Path root;
    private final Path contentsDir;
    private final Path javaDir;
    private final Path resourcesDir;
    private final Path macOSDir;
    private final Path runtimeDir;
    private final Path runtimeRoot;
    private final Path mdir;

    private final Map<String, ? super Object> params;

    private static List<String> keyChains;

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

    public static final BundlerParamInfo<Boolean> MAC_CONFIGURE_LAUNCHER_IN_PLIST = new StandardBundlerParam<>(
            "Configure Launcher in Info.plist",
            "Should the legacy method of configuring hte launcher in the Info.plist be used.",
            "mac.configure-launcher-in-plist",
            Boolean.class,
            params -> Boolean.FALSE,
            (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<String> MAC_CATEGORY = new StandardBundlerParam<>(
            "Category",
            "Mac App Store Categories. Note that the key is the string to display to the user and the value " +
                    "is the id of the category.",
            "mac.category",
            String.class,
            CATEGORY::fetchFrom,
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME = new StandardBundlerParam<>(
            "CFBundleName",
            "The name of the app as it appears in the Menu Bar.  This can be different from the application " +
                    "name.  This name should be less than 16 characters long and be suitable for displaying " +
                    "in the menu bar and the app's Info window.",
            "mac.CFBundleName",
            String.class,
            params -> null,
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER = new StandardBundlerParam<>(
            "CFBundleIdentifier",
            "An identifier that uniquely identifies the application for MacOSX (and on the Mac App Store)." +
                    "  May only use alphanumeric (A-Z,a-z,0-9), hyphen (-), and period (.) characters.",
            "mac.CFBundleIdentifier",
            String.class,
            IDENTIFIER::fetchFrom,
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_VERSION = new StandardBundlerParam<>(
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

    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>("",
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

//    public static final BundlerParamInfo<String> DEVELOPER_ID_APP_SIGNING_KEY = new StandardBundlerParam<>(
//            "Apple Developer ID Application Signing Key",
//            "The full name of the Apple Developer ID Application signing key.",
//            "mac.signing-key-developer-id-app",
//            String.class,
//            params -> MacBaseInstallerBundler.findKey("Developer ID Application: " + SIGNING_KEY_USER.fetchFrom(params), SIGNING_KEYCHAIN.fetchFrom(params), VERBOSE.fetchFrom(params)),
//            (s, p) -> s);

    //    public static final BundlerParamInfo<String> BUNDLE_ID_SIGNING_PREFIX = new StandardBundlerParam<>(
//            "Bundle Signing Prefix",
//            "When signing the application bundle this value is prefixed to all components that need to be signed that don't have an existing CFBundleIdentifier.",
//            "mac.bundle-id-signing-prefix",
//            String.class,
//            params -> IDENTIFIER.fetchFrom(params) + ".",
//            (s, p) -> s);
//
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

    public MacAppImageBuilder(Map<String, Object> config, Path imageOutDir) throws IOException {
        super(config, imageOutDir.resolve(APP_FS_NAME.fetchFrom(config) + ".app/Contents/PlugIns/Java.runtime/Contents/Home"));

        Objects.requireNonNull(imageOutDir);

        this.params = config;
        this.root = imageOutDir.resolve(APP_FS_NAME.fetchFrom(params) + ".app");
        this.contentsDir = root.resolve("Contents");
        this.javaDir = contentsDir.resolve("Java");
        this.resourcesDir = contentsDir.resolve("Resources");
        this.macOSDir = contentsDir.resolve("MacOS");
        this.runtimeDir = contentsDir.resolve("PlugIns/Java.runtime");
        this.runtimeRoot = runtimeDir.resolve("Contents/Home");
        this.mdir = runtimeRoot.resolve("lib");
        Files.createDirectories(javaDir);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(macOSDir);
        Files.createDirectories(runtimeDir);
    }

    private static boolean validCFBundleVersion(String v) {
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

        String[] p = v.split("\\.");
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
    public void prepareApplicationFiles() throws IOException {
        File file;

        // Generate PkgInfo
        File pkgInfoFile = new File(contentsDir.toFile(), "PkgInfo");
        pkgInfoFile.createNewFile();
        writePkgInfo(pkgInfoFile);

        Path executable = macOSDir.resolve(getLauncherName(params));

        Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/mac/" + EXECUTABLE_NAME),
                executable);
        Files.copy(Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/mac/" + LIBRARY_NAME),
                macOSDir.resolve(LIBRARY_NAME));
        executable.toFile().setExecutable(true, false);

        // generate launcher config
        writeCfgFile(params, new File(root.toFile(), getLauncherCfgName(params)), "$APPDIR/PlugIns/Java.runtime");

        // Copy class path entries to Java folder
        copyClassPathEntries(javaDir);

        // Copy icon to Resources folder
        File icon = ICON_ICNS.fetchFrom(params);
        InputStream in = locateResource("packager/mac/" + APP_NAME.fetchFrom(params) + ".icns",
                "icon",
                DEFAULT_ICNS_ICON.fetchFrom(params),
                icon,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        Files.copy(in, resourcesDir.resolve(APP_NAME.fetchFrom(params) + ".icns"));

        // copy file association icons
        for (Map<String, ? super Object> fa : FILE_ASSOCIATIONS.fetchFrom(params)) {
            file = FA_ICON.fetchFrom(fa);
            if (file != null && file.exists()) {
                try (InputStream in2 = new FileInputStream(file)) {
                    Files.copy(in2, resourcesDir.resolve(file.getName()));
                }

            }
        }

        // Generate Info.plist
        writeInfoPlist(contentsDir.resolve("Info.plist").toFile());

        // generate java runtime info.plist
        writeRuntimeInfoPlist(runtimeDir.resolve("Contents/Info.plist").toFile());

        // copy library
        Path runtimeMacOSDir = Files.createDirectories(runtimeDir.resolve("Contents/MacOS"));
        Files.copy(runtimeRoot.resolve("lib/jli/libjli.dylib"), runtimeMacOSDir.resolve("libjli.dylib"));

        // maybe sign
        if (Optional.ofNullable(SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
            try {
                addNewKeychain(params);
            } catch (InterruptedException e) {
                Log.error(e.getMessage());
            }
            String signingIdentity = DEVELOPER_ID_APP_SIGNING_KEY.fetchFrom(params);
            if (signingIdentity != null) {
                signAppBundle(params, root, signingIdentity, BUNDLE_ID_SIGNING_PREFIX.fetchFrom(params), null, null);
            }
            restoreKeychainList(params);
        }
    }


    private String getLauncherName(Map<String, ? super Object> params) {
        if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            return MAIN_CLASS.fetchFrom(params);
        }
    }

    private static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "Contents/Java/" + APP_NAME.fetchFrom(p) + ".cfg";
    }

    private void copyClassPathEntries(Path javaDirectory) throws IOException {
        List<RelativeFileSet> resourcesList = APP_RESOURCES_LIST.fetchFrom(params);
        if (resourcesList == null) {
            throw new RuntimeException("Null app resources?");
        }

        for (RelativeFileSet classPath : resourcesList) {
            File srcdir = classPath.getBaseDirectory();
            for (String fname : classPath.getIncludedFiles()) {
                copyEntry(javaDirectory, srcdir, fname);
            }
        }
    }

    private String getBundleName(Map<String, ? super Object> params) {
        if (MAC_CF_BUNDLE_NAME.fetchFrom(params) != null) {
            String bn = MAC_CF_BUNDLE_NAME.fetchFrom(params);
            if (bn.length() > 16) {
                Log.info(MessageFormat.format("{0} is set to ''{1}'', which is longer than 16 characters.  " +
                        "For a better Mac experience consider shortening it.", MAC_CF_BUNDLE_NAME.getID(), bn));
            }
            return MAC_CF_BUNDLE_NAME.fetchFrom(params);
        } else if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            String nm = MAIN_CLASS.fetchFrom(params);
            if (nm.length() > 16) {
                nm = nm.substring(0, 16);
            }
            return nm;
        }
    }

    private void writeRuntimeInfoPlist(File file) throws IOException {
        Map<String, String> data = new HashMap<>();
        data.put("CF_BUNDLE_IDENTIFIER", "com.oracle.java." + MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params));
        data.put("CF_BUNDLE_NAME", "Java Runtime Image");
        data.put("CF_BUNDLE_VERSION", VERSION.fetchFrom(params));
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", VERSION.fetchFrom(params));

        Writer w = new BufferedWriter(new FileWriter(file));
        w.write(preprocessTextResource(
                "packager/mac/Runtime-Info.plist",
                "Java Runtime Info.plist",
                TEMPLATE_RUNTIME_INFO_PLIST,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params)));
        w.close();
    }

    private void writeInfoPlist(File file) throws IOException {
        Log.verbose(MessageFormat.format("Preparing Info.plist: {0}", file.getAbsolutePath()));

        //prepare config for exe
        //Note: do not need CFBundleDisplayName if we do not support localization
        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ICON_FILE", APP_NAME.fetchFrom(params) + ".icns");
        data.put("DEPLOY_BUNDLE_IDENTIFIER",
                MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_NAME",
                getBundleName(params));
        data.put("DEPLOY_BUNDLE_COPYRIGHT",
                COPYRIGHT.fetchFrom(params) != null ? COPYRIGHT.fetchFrom(params) : "Unknown");
        data.put("DEPLOY_LAUNCHER_NAME", getLauncherName(params));
        data.put("DEPLOY_JAVA_RUNTIME_NAME", "$APPDIR/PlugIns/Java.runtime");
        data.put("DEPLOY_BUNDLE_SHORT_VERSION",
                VERSION.fetchFrom(params) != null ? VERSION.fetchFrom(params) : "1.0.0");
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION",
                MAC_CF_BUNDLE_VERSION.fetchFrom(params) != null ? MAC_CF_BUNDLE_VERSION.fetchFrom(params) : "100");
        data.put("DEPLOY_BUNDLE_CATEGORY", MAC_CATEGORY.fetchFrom(params));

        boolean hasMainJar = MAIN_JAR.fetchFrom(params) != null;
        boolean hasMainModule = StandardBundlerParam.MODULE.fetchFrom(params) != null;

        if (hasMainJar) {
            data.put("DEPLOY_MAIN_JAR_NAME", MAIN_JAR.fetchFrom(params).getIncludedFiles().iterator().next());
        }
        else if (hasMainModule) {
            data.put("DEPLOY_MODULE_NAME", StandardBundlerParam.MODULE.fetchFrom(params));
        }

        data.put("DEPLOY_PREFERENCES_ID", PREFERENCES_ID.fetchFrom(params).toLowerCase());

        StringBuilder sb = new StringBuilder();
        List<String> jvmOptions = JVM_OPTIONS.fetchFrom(params);

        String newline = ""; //So we don't add unneccessary extra line after last append
        for (String o : jvmOptions) {
            sb.append(newline).append("    <string>").append(o).append("</string>");
            newline = "\n";
        }

        Map<String, String> jvmProps = JVM_PROPERTIES.fetchFrom(params);
        for (Map.Entry<String, String> entry : jvmProps.entrySet()) {
            sb.append(newline)
                    .append("    <string>-D")
                    .append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append("</string>");
            newline = "\n";
        }

        String preloader = PRELOADER_CLASS.fetchFrom(params);
        if (preloader != null) {
            sb.append(newline)
                    .append("    <string>-Djavafx.preloader=")
                    .append(preloader)
                    .append("</string>");
        }

        data.put("DEPLOY_JVM_OPTIONS", sb.toString());

        sb = new StringBuilder();
        List<String> args = ARGUMENTS.fetchFrom(params);
        newline = ""; //So we don't add unneccessary extra line after last append
        for (String o : args) {
            sb.append(newline).append("    <string>").append(o).append("</string>");
            newline = "\n";
        }
        data.put("DEPLOY_ARGUMENTS", sb.toString());

        newline = "";
        sb = new StringBuilder();
        Map<String, String> overridableJVMOptions = USER_JVM_OPTIONS.fetchFrom(params);
        for (Map.Entry<String, String> arg : overridableJVMOptions.entrySet()) {
            sb.append(newline)
                    .append("      <key>").append(arg.getKey()).append("</key>\n")
                    .append("      <string>").append(arg.getValue()).append("</string>");
            newline = "\n";
        }
        data.put("DEPLOY_JVM_USER_OPTIONS", sb.toString());


        data.put("DEPLOY_LAUNCHER_CLASS", MAIN_CLASS.fetchFrom(params));

        StringBuilder macroedPath = new StringBuilder();
        for (String s : CLASSPATH.fetchFrom(params).split("[ ;:]+")) {
            macroedPath.append(s);
            macroedPath.append(":");
        }
        macroedPath.deleteCharAt(macroedPath.length() - 1);

        data.put("DEPLOY_APP_CLASSPATH", macroedPath.toString());

        StringBuilder bundleDocumentTypes = new StringBuilder();
        StringBuilder exportedTypes = new StringBuilder();
        for (Map<String, ? super Object> fileAssociation : FILE_ASSOCIATIONS.fetchFrom(params)) {

            List<String> extensions = FA_EXTENSIONS.fetchFrom(fileAssociation);

            if (extensions == null) {
                Log.info("Creating association with null extension.");
            }

            List<String> mimeTypes = FA_CONTENT_TYPE.fetchFrom(fileAssociation);
            String itemContentType = MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params) + "." + ((extensions == null || extensions.isEmpty())
                    ? "mime"
                    : extensions.get(0));
            String description = FA_DESCRIPTION.fetchFrom(fileAssociation);
            File icon = FA_ICON.fetchFrom(fileAssociation); //TODO FA_ICON_ICNS

            bundleDocumentTypes.append("    <dict>\n")
                    .append("      <key>LSItemContentTypes</key>\n")
                    .append("      <array>\n")
                    .append("        <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("      </array>\n")
                    .append("\n")
                    .append("      <key>CFBundleTypeName</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>LSHandlerRank</key>\n")
                    .append("      <string>Owner</string>\n") //TODO make a bundler arg
                    .append("\n")
                    .append("      <key>CFBundleTypeRole</key>\n")
                    .append("      <string>Editor</string>\n") // TODO make a bundler arg
                    .append("\n")
                    .append("      <key>LSIsAppleDefaultForType</key>\n")
                    .append("      <true/>\n") // TODO make a bundler arg
                    .append("\n");

            if (icon != null && icon.exists()) {
                //?
                bundleDocumentTypes.append("      <key>CFBundleTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n");
            }
            bundleDocumentTypes.append("    </dict>\n");

            exportedTypes.append("    <dict>\n")
                    .append("      <key>UTTypeIdentifier</key>\n")
                    .append("      <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>UTTypeDescription</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("      <key>UTTypeConformsTo</key>\n")
                    .append("      <array>\n")
                    .append("          <string>public.data</string>\n") //TODO expose this?
                    .append("      </array>\n")
                    .append("\n");

            if (icon != null && icon.exists()) {
                exportedTypes.append("      <key>UTTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n")
                        .append("\n");
            }

            exportedTypes.append("\n")
                    .append("      <key>UTTypeTagSpecification</key>\n")
                    .append("      <dict>\n")
                            //TODO expose via param? .append("        <key>com.apple.ostype</key>\n");
                            //TODO expose via param? .append("        <string>ABCD</string>\n")
                    .append("\n");

            if (extensions != null && !extensions.isEmpty()) {
                exportedTypes.append("        <key>public.filename-extension</key>\n")
                        .append("        <array>\n");

                for (String ext : extensions) {
                    exportedTypes.append("          <string>")
                            .append(ext)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            if (mimeTypes != null && !mimeTypes.isEmpty()) {
                exportedTypes.append("        <key>public.mime-type</key>\n")
                        .append("        <array>\n");

                for (String mime : mimeTypes) {
                    exportedTypes.append("          <string>")
                            .append(mime)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            exportedTypes.append("      </dict>\n")
                    .append("    </dict>\n");
        }
        String associationData;
        if (bundleDocumentTypes.length() > 0) {
            associationData = "\n  <key>CFBundleDocumentTypes</key>\n  <array>\n"
                    + bundleDocumentTypes.toString()
                    + "  </array>\n\n  <key>UTExportedTypeDeclarations</key>\n  <array>\n"
                    + exportedTypes.toString()
                    + "  </array>\n";
        } else {
            associationData = "";
        }
        data.put("DEPLOY_FILE_ASSOCIATIONS", associationData);


        Writer w = new BufferedWriter(new FileWriter(file));
        w.write(preprocessTextResource(
                "packager/mac/Info.plist",
                "Application Info.plist",
                TEMPLATE_INFO_PLIST_LITE,
                data, VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params)));
        w.close();
    }

    private void writePkgInfo(File file) throws IOException {
        // hardcoded as it does not seem we need to change it ever
        String signature = "????";

        try (Writer out = new BufferedWriter(new FileWriter(file))) {
            out.write(OS_TYPE_CODE + signature);
            out.flush();
        }
    }

    public static void addNewKeychain(Map<String, ? super Object> params)
                                    throws IOException, InterruptedException {
        if (Platform.getMajorVersion() < 10 ||
            (Platform.getMajorVersion() == 10 && Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);
        if (keyChain == null || keyChain.isEmpty()) {
            return;
        }

        // get current keychain list
        String keyChainPath = new File(keyChain).getAbsolutePath();
        List<String> keychainList = new ArrayList<>();
        int ret = IOUtils.getProcessOutput(keychainList, "security", "list-keychains");
        if (ret != 0) {
            Log.error("Error: unable to get keychain list.");
            return;
        }

        boolean contains = keychainList.stream().anyMatch(
                    str -> str.trim().equals("\""+keyChainPath.trim()+"\""));
        if (contains) {
            // keychain is already added in the search list
            return;
        }

        keyChains = new ArrayList<>();
        // remove "
        keychainList.forEach((String s) -> {
            String path = s.trim();
            if (path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length()-1);
            }
            keyChains.add(path);
        });

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);
        args.add(keyChain);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));
    }

    public static void restoreKeychainList(Map<String, ? super Object> params) throws IOException{
        if (Platform.getMajorVersion() < 10 ||
            (Platform.getMajorVersion() == 10 && Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        if (keyChains == null || keyChains.isEmpty()) {
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));
    }

    public static void signAppBundle(Map<String, ? super Object> params, Path appLocation, String signingIdentity, String identifierPrefix, String entitlementsFile, String inheritedEntitlements) throws IOException {
        AtomicReference<IOException> toThrow = new AtomicReference<>();
        String appExecutable = "/Contents/MacOS/" + APP_NAME.fetchFrom(params);
        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);

        // sign all dylibs and jars
        Files.walk(appLocation)
                // fix permissions
                .peek(path -> {
                    try {
                        Set<PosixFilePermission> pfp = Files.getPosixFilePermissions(path);
                        if (!pfp.contains(PosixFilePermission.OWNER_WRITE)) {
                            pfp = EnumSet.copyOf(pfp);
                            pfp.add(PosixFilePermission.OWNER_WRITE);
                            Files.setPosixFilePermissions(path, pfp);
                        }
                    } catch (IOException e) {
                        Log.debug(e);
                    }
                })
                .filter(p -> Files.isRegularFile(p) &&
                                !(p.toString().contains("/Contents/MacOS/libjli.dylib")
                                        || p.toString().contains("/Contents/MacOS/JavaAppletPlugin")
                                        || p.toString().endsWith(appExecutable))
                ).forEach(p -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            // If p is a symlink then skip the signing process.
            if (Files.isSymbolicLink(p)) {
                if (VERBOSE.fetchFrom(params)) {
                    Log.verbose(MessageFormat.format("Warning: codesign is skipping the symlink {0}",
                            p.toString()));
                }
            }
            else {
                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix, // use the identifier as a prefix
                        "-vvvv"));
                if (entitlementsFile != null &&
                        (p.toString().endsWith(".jar")
                                || p.toString().endsWith(".dylib"))) {
                    args.add("--entitlements");
                    args.add(entitlementsFile); // entitlements
                } else if (inheritedEntitlements != null && Files.isExecutable(p)) {
                    args.add("--entitlements");
                    args.add(inheritedEntitlements); // inherited entitlements for executable processes
                }
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(p.toString());

                try {
                    Set<PosixFilePermission> oldPermissions = Files.getPosixFilePermissions(p);
                    File f = p.toFile();
                    f.setWritable(true, true);

                    ProcessBuilder pb = new ProcessBuilder(args);
                    IOUtils.exec(pb, VERBOSE.fetchFrom(params));

                    Files.setPosixFilePermissions(p, oldPermissions);
                } catch (IOException ioe) {
                    toThrow.set(ioe);
                }
            }
        });

        IOException ioe = toThrow.get();
        if (ioe != null) {
            throw ioe;
        }

        // sign all plugins and frameworks
        Consumer<? super Path> signIdentifiedByPList = path -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            try {
                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix, // use the identifier as a prefix
                        "-vvvv"));
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(path.toString());
                ProcessBuilder pb = new ProcessBuilder(args);
                IOUtils.exec(pb, VERBOSE.fetchFrom(params));

                args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix, // use the identifier as a prefix
                        "-vvvv"));
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(path.toString() + "/Contents/_CodeSignature/CodeResources");
                pb = new ProcessBuilder(args);
                IOUtils.exec(pb, VERBOSE.fetchFrom(params));
            } catch (IOException e) {
                toThrow.set(e);
            }
        };

        Path pluginsPath = appLocation.resolve("Contents/PlugIns");
        if (Files.isDirectory(pluginsPath)) {
            Files.list(pluginsPath)
                    .forEach(signIdentifiedByPList);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }
        Path frameworkPath = appLocation.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            Files.list(frameworkPath)
                    .forEach(signIdentifiedByPList);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }

        // sign the app itself
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("codesign",
                "-s", signingIdentity, // sign with this key
                "-vvvv")); // super verbose output
        if (entitlementsFile != null) {
            args.add("--entitlements");
            args.add(entitlementsFile); // entitlements
        }
        if (keyChain != null && !keyChain.isEmpty()) {
            args.add("--keychain");
            args.add(keyChain);
        }
        args.add(appLocation.toString());

        ProcessBuilder pb = new ProcessBuilder(args.toArray(new String[args.size()]));
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));
    }

    @Override
    protected String getCacheLocation(Map<String, ? super Object> params) {
        return "$CACHEDIR/";
    }
}
