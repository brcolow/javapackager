/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import com.sun.openjfx.tools.packager.bundlers.Bundler.BundleType;

import static com.sun.openjfx.tools.packager.Help.HELP_MSG;

public class Main {

    private static final String CREATE_BSS_INTERNAL = "-createbss_internal";
    private static final String CREATE_BSS_EXTERNAL = "-createbss";
    private static final String VERSION = "Java Packager version " + PackagerLib.JAVAFX_VERSION + "\n";

    private static boolean verbose;
    private static boolean genPackages;
    private static boolean css2Bin;
    private static boolean signJar;

    private static void addResources(CommonParams commonParams, String srcdir, String srcfiles)
            throws PackagerException {
        if (srcdir == null || "".equals(srcdir)) {
            return;
        }

        File baseDir = new File(srcdir);

        if (!baseDir.isDirectory()) {
            Log.info("Unable to add resources: \"-srcdir\" is not a directory.");
            throw new PackagerException("Unable to add resources: \"-srcdir\" is not a directory but was: " + srcdir);
        }

        List<String> fileNames;
        if (srcfiles != null) {
            fileNames = Arrays.asList(srcfiles.split(File.pathSeparator));
        } else {
            // "-srcfiles" is omitted, all files in srcdir (which
            // is a mandatory argument in this case) will be packaged.
            fileNames = new ArrayList<>();
            try (Stream<Path> files = Files.list(baseDir.toPath())) {
                files.forEach(file -> fileNames.add(file.getFileName().toString()));
            } catch (IOException e) {
                Log.info("Unable to add resources: " + e.getMessage());
            }
        }

        fileNames.forEach(file -> commonParams.addResource(baseDir, file));
    }

    private static void addArgument(DeployParams deployParams, String argument) {
        if (deployParams.arguments != null) {
            deployParams.arguments.add(argument);
        } else {
            List<String> list = new LinkedList<>();
            list.add(argument);
            deployParams.setArguments(list);
        }
    }

    private static List<Param> parseParams(String filename) throws IOException {
        File paramFile = new File(filename);
        Properties properties = new Properties();
        FileInputStream in = new FileInputStream(paramFile);
        properties.load(in);
        in.close();

        List<Param> parameters = new ArrayList<>(properties.size());

        for (Map.Entry entry : properties.entrySet()) {
            Param param = new Param();
            param.setName((String) entry.getKey());
            param.setValue((String) entry.getValue());
            parameters.add(param);
        }
        return parameters;
    }

    private static List<HtmlParam> parseHtmlParams(String filename) throws IOException {
        File paramFile = new File(filename);
        Properties properties = new Properties();
        FileInputStream in = new FileInputStream(paramFile);
        properties.load(in);
        in.close();

        List<HtmlParam> parameters = new ArrayList<>(properties.size());

        for (Map.Entry entry : properties.entrySet()) {
            HtmlParam param = new HtmlParam();
            param.setName((String) entry.getKey());
            param.setValue((String) entry.getValue());
            parameters.add(param);
        }
        return parameters;
    }

    public static void main(String... args) throws Exception {
        // Create logger with default system.out and system.err
        Log.Logger logger = new Log.Logger(false);
        Log.setLogger(logger);

        int status = run(args);
        System.exit(status);
    }

    public static int run(PrintWriter out, PrintWriter err, String... args) throws Exception {
        // Create logger with provided streams
        Log.Logger logger = new Log.Logger(false);
        logger.setPrintWriter(out, err);
        Log.setLogger(logger);

        int status = run(args);
        Log.flush();
        return status;
    }

    private static int relaunchJavapackager(String... args) throws Exception {
        final String exe = Platform.getPlatform() == Platform.WINDOWS ? ".exe" : "";
        String javaHome = System.getProperty("java.home");
        if (javaHome == null) {
            throw new PackagerException("Error: Java home directory is not known.");
        }

        final File java = new File(new File(javaHome), "bin/java" + exe);

        // compose new arguments
        List<String> newArgs = new ArrayList<>();
        newArgs.add(java.getAbsolutePath());
        newArgs.add("--add-modules");
        newArgs.add("javafx.graphics");
        newArgs.add("-m");
        newArgs.add("com.brcolow.javapackager/com.sun.openjfx.tools.packager.Main");

        for (String arg : args) {
            if (arg.equalsIgnoreCase(CREATE_BSS_EXTERNAL)) {
                newArgs.add(CREATE_BSS_INTERNAL);
            } else {
                newArgs.add(arg);
            }
        }

        int ret = IOUtils.execute(newArgs);
        if (ret != 0) {
            throw new PackagerException("Error: Conversion of CSS files to binary form failed");
        }

        return ret;
    }

    public static int run(String... args) throws Exception {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(CREATE_BSS_EXTERNAL)) {
                return relaunchJavapackager(args);
            }
        }

        BundleType bundleType = BundleType.NONE;

        if (args.length == 0 || args.length == 1 && (args[0].equals("-help") || args[0].equals("--help"))) {
            Log.info(HELP_MSG);
        } else if (args.length == 1 && (args[0].equals("-version") || args[0].equals("--version"))) {
            Log.info(VERSION);
        } else {
            PackagerLib packager = new PackagerLib();
            DeployParams deployParams = new DeployParams();
            CreateBSSParams createBssParams = new CreateBSSParams();
            SignJarParams signJarParams = new SignJarParams();
            String srcdir = null;
            String srcfiles = null;

            try {
                if (args[0].equalsIgnoreCase("-deploy")) {
                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if (arg.startsWith("-B")) {
                            String key;
                            String value;

                            int keyStart = 2;
                            int equals = arg.indexOf("=");
                            int len = arg.length();
                            if (equals < keyStart) {
                                if (keyStart < len) {
                                    key = arg.substring(keyStart, len);
                                    value = Boolean.TRUE.toString();
                                } else {
                                    continue;
                                }
                            } else if (keyStart < equals) {
                                key = arg.substring(keyStart, equals);
                                value = arg.substring(equals + 1, len);
                            } else {
                                continue;
                            }
                            deployParams.addBundleArgument(key, value);
                        } else if (arg.equalsIgnoreCase("-title")) {
                            deployParams.setTitle(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-vendor")) {
                            deployParams.setVendor(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-native")) {
                            bundleType = BundleType.NATIVE;
                            String format = null; // null means ANY
                            if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                                String v = args[++i];
                                com.sun.openjfx.tools.packager.bundlers.Bundler.Bundle bundle =
                                        com.sun.openjfx.tools.packager.bundlers.Bundler.stringToBundle(v);
                                bundleType = bundle.type;
                                format = bundle.format;
                            }
                            deployParams.setBundleType(bundleType);
                            deployParams.setTargetFormat(format);
                        } else if (arg.equalsIgnoreCase("-description")) {
                            deployParams.setDescription(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-appclass")) {
                            deployParams.setApplicationClass(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-daemon")) {
                            deployParams.setServiceHint(true);
                        } else if (arg.equalsIgnoreCase("-singleton")) {
                            deployParams.setSingleton(true);
                        } else if (arg.equalsIgnoreCase("-installdirChooser")) {
                            deployParams.setInstalldirChooser(true);
                        } else if (arg.equalsIgnoreCase("-preloader")) {
                            deployParams.setPreloader(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-paramFile")) {
                            deployParams.setParams(parseParams(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-htmlParamFile")) {
                            deployParams.setHtmlParams(parseHtmlParams(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-width")) {
                            deployParams.setWidth(Integer.parseInt(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-height")) {
                            deployParams.setHeight(Integer.parseInt(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-name")) {
                            deployParams.setAppName(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-appId") || arg.equalsIgnoreCase("-templateId")) {
                            String appIdArg = nextArg(args, i++);
                            deployParams.setAppId(appIdArg);
                            deployParams.setId(appIdArg);
                        } else if (arg.equalsIgnoreCase("-verbose") || arg.equalsIgnoreCase("-v")) {
                            deployParams.setVerbose(true);
                            verbose = true;
                        } else if (arg.equalsIgnoreCase("-outdir")) {
                            deployParams.setOutdir(new File(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-outfile")) {
                            deployParams.setOutfile(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-" + StandardBundlerParam.SOURCE_DIR.getID())) {
                            srcdir = nextArg(args, i++);
                            deployParams.srcdir = srcdir;
                        } else if (arg.equalsIgnoreCase("-srcfiles")) {
                            srcfiles = nextArg(args, i++);
                        } else if (arg.equalsIgnoreCase("-argument")) {
                            addArgument(deployParams, nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-nosign")) {
                            deployParams.setSignBundle(false);
                        } else if (arg.equals(ADD_MODULES)) {
                            deployParams.addAddModule(nextArg(args, i++));
                        } else if (arg.startsWith(ADD_MODULES + "=")) {
                            deployParams.addAddModule(arg.replace(ADD_MODULES + "=", ""));
                        } else if (arg.equals(LIMIT_MODULES)) {
                            deployParams.addLimitModule(nextArg(args, i++));
                        } else if (arg.startsWith(LIMIT_MODULES + "=")) {
                            deployParams.addLimitModule(arg.replace(LIMIT_MODULES + "=", ""));
                        } else if (arg.equals(STRIP_NATIVE_COMMANDS)) {
                            deployParams.setStripNativeCommands(Boolean.valueOf(nextArg(args, i++)));
                        } else if (arg.equals(STRIP_NATIVE_COMMANDS + "=")) {
                            deployParams.setStripNativeCommands(
                                    Boolean.valueOf(arg.replace(STRIP_NATIVE_COMMANDS + "=", "")));
                        } else if (arg.equals(DETECT_MODULES)) {
                            deployParams.setDetectModules(true);
                        } else if (arg.equals(MODULE_PATH) || arg.equals(P)) {
                            deployParams.modulePath = nextArg(args, i++);
                        } else if (arg.equals(MODULE_PATH + "=")) {
                            deployParams.modulePath = arg.replace(MODULE_PATH + "=", "");
                        } else if (arg.equals(MODULE) || arg.equals(M)) {
                            deployParams.setModule(nextArg(args, i++));
                        } else if (arg.equals(MODULE + "=")) {
                            deployParams.setModule(arg.replace(MODULE + "=", ""));
                        } else if (arg.startsWith(J_XDEBUG)) {
                            deployParams.setDebug(arg.replace(J_XDEBUG, ""));
                        } else {
                            throw new PackagerException("Error: Unknown argument: {0}", arg);
                        }
                    }

                    if (deployParams.validateForBundle()) {
                        genPackages = true;
                    }

                    addResources(deployParams, srcdir, srcfiles);
                } else if (args[0].equalsIgnoreCase(CREATE_BSS_INTERNAL)) {
                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if (arg.equalsIgnoreCase("-verbose") || arg.equalsIgnoreCase("-v")) {
                            createBssParams.setVerbose(true);
                            verbose = true;
                        } else if (arg.equalsIgnoreCase("-outdir")) {
                            createBssParams.setOutdir(new File(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-srcdir")) {
                            srcdir = nextArg(args, i++);
                        } else if (arg.equalsIgnoreCase("-srcfiles")) {
                            srcfiles = nextArg(args, i++);
                        } else {
                            throw new PackagerException("Error: Unknown argument: {0}", arg);
                        }
                    }

                    addResources(createBssParams, srcdir, srcfiles);
                    css2Bin = true;
                } else if (args[0].equalsIgnoreCase("-signJar")) {
                    for (int i = 1; i < args.length; i++) {
                        String arg = args[i];
                        if (arg.equalsIgnoreCase("-keyStore")) {
                            signJarParams.setKeyStore(new File(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-alias")) {
                            signJarParams.setAlias(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-storePass")) {
                            signJarParams.setStorePass(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-keyPass")) {
                            signJarParams.setKeyPass(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-storeType")) {
                            signJarParams.setStoreType(nextArg(args, i++));
                        } else if (arg.equalsIgnoreCase("-verbose") || arg.equalsIgnoreCase("-v")) {
                            signJarParams.setVerbose(true);
                            verbose = true;
                        } else if (arg.equalsIgnoreCase("-outdir")) {
                            signJarParams.setOutdir(new File(nextArg(args, i++)));
                        } else if (arg.equalsIgnoreCase("-srcdir")) {
                            srcdir = nextArg(args, i++);
                        } else if (arg.equalsIgnoreCase("-srcfiles")) {
                            srcfiles = nextArg(args, i++);
                        } else {
                            throw new PackagerException("Error: Unknown argument: {0}", arg);
                        }
                    }

                    addResources(signJarParams, srcdir, srcfiles);
                    signJar = true;
                } else if (args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("--help")) {
                    showBundlerHelp(args[1], args.length > 2 && "-verbose".equals(args[2]));
                } else {
                    Log.error(MessageFormat.format("Error: Unknown command: {0}", args[0]));
                    return -1;
                }

                // Enable verbose if needed
                if (verbose) {
                    Log.Logger logger = Log.getLogger();
                    if (logger != null) {
                        logger.setVerbose(true);
                    }
                }

                if (css2Bin) {
                    createBssParams.validate();
                    packager.generateBSS(createBssParams);
                }
                if (genPackages) {
                    deployParams.setBundleType(bundleType);
                    deployParams.validate();
                    packager.generateDeploymentPackages(deployParams);
                }
                if (signJar) {
                    signJarParams.validate();
                    if (signJarParams.storePass == null) {
                        char[] passwd = System.console().readPassword("Enter Passphrase for keystore:");
                        if (passwd == null) {
                            signJarParams.storePass = "";
                        } else {
                            signJarParams.storePass = new String(passwd);
                        }
                    }
                    if (signJarParams.keyPass == null) {
                        char[] passwd = System.console().readPassword(
                                "Enter key password for %s:", signJarParams.alias);
                        if (passwd == null) {
                            signJarParams.keyPass = "";
                        } else {
                            signJarParams.keyPass = new String(passwd);
                        }
                    }
                    packager.signJar(signJarParams);
                }

            } catch (Exception e) {
                if (verbose) {
                    throw e;
                } else {
                    Log.error(e.getMessage());
                    if (e.getCause() != null && e.getCause() != e) {
                        Log.error(e.getCause().getMessage());
                    }
                    return -1;
                }
            }
        }

        return 0;
    }

    private static final String MODULE = "--" + StandardBundlerParam.MODULE.getID();
    private static final String M = "-m";
    private static final String MODULE_PATH = "--" + StandardBundlerParam.MODULE_PATH.getID();
    private static final String P = "-p";
    private static final String ADD_MODULES = "--" + StandardBundlerParam.ADD_MODULES.getID();
    private static final String LIMIT_MODULES = "--" + StandardBundlerParam.LIMIT_MODULES.getID();
    private static final String STRIP_NATIVE_COMMANDS = "--" + StandardBundlerParam.STRIP_NATIVE_COMMANDS.getID();
    private static final String J_XDEBUG = JLinkBundlerHelper.DEBUG.getID() + ":";
    private static final String DETECT_MODULES = "--" + JLinkBundlerHelper.DETECT_MODULES.getID();

    private static void showBundlerHelp(String bundlerName, boolean verbose) {
        if ("bundlers".equals(bundlerName)) {
            // enumerate bundlers
            Log.info("Known Bundlers -- \n");
            for (Bundler bundler : Bundlers.createBundlersInstance().getBundlers()) {
                try {
                    bundler.validate(new HashMap<>());
                } catch (UnsupportedPlatformException upe) {
                    // don't list bundlers this platform cannot run
                    continue;
                } catch (ConfigException ignore) {
                }

                if (verbose) {
                    Log.infof("%s - %s - %s\n\t%s\n", bundler.getID(), bundler.getName(),
                            bundler.getBundleType(), bundler.getDescription());
                } else {
                    Log.infof("%s - %s - %s\n", bundler.getID(), bundler.getName(), bundler.getBundleType());
                }
            }
        } else {
            // enumerate parameters for a bundler
            for (Bundler bundler : Bundlers.createBundlersInstance().getBundlers()) {
                if (bundler.getID().equals(bundlerName)) {
                    Log.infof("Bundler Parameters for %s (%s) --\n", bundler.getName(), bundler.getID());
                    for (BundlerParamInfo bpi : bundler.getBundleParameters()) {
                        if (bpi.getStringConverter() == null) {
                            continue;
                        }
                        if (verbose) {
                            Log.infof("%s - %s - %s\n\t%s\n", bpi.getID(), bpi.getName(),
                                    bpi.getValueType().getSimpleName(), bpi.getDescription());
                        } else {
                            Log.infof("%s - %s - %s\n", bpi.getID(), bpi.getName(), bpi.getValueType().getSimpleName());
                        }
                    }
                    return;
                }
            }
            Log.infof("Sorry, no bundler matching the id %s was found.\n", bundlerName);
        }
    }

    private static String nextArg(String[] args, int i) {
        return i == args.length - 1 ? "" : args[i + 1];
    }
}
