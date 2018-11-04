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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.sun.openjfx.tools.packager.bundlers.BundleParams;
import com.sun.openjfx.tools.packager.bundlers.Bundler.BundleType;

public class DeployParams extends CommonParams {

    private final List<RelativeFileSet> resources = new ArrayList<>();
    String id;
    String title;
    String vendor;
    String email;
    String description;
    String category;
    String licenseType;
    String copyright;
    String version;
    Boolean systemWide;
    Boolean serviceHint;
    Boolean signBundle;
    Boolean installdirChooser;
    Boolean singleton;
    String applicationClass;
    String preloader;
    public List<Param> params;
    List<String> arguments; // unnamed arguments

    // Java 9 modules support
    String addModules;
    String limitModules;
    Boolean stripNativeCommands;
    Boolean detectmods;
    String modulePath;
    String module;
    String debugPort;
    String srcdir;

    String appName;
    boolean isExtension;
    Boolean needShortcut;
    Boolean needMenu;
    String outfile;
    String jrePlatform = PackagerLib.JAVAFX_VERSION + "+";
    String fxPlatform = PackagerLib.JAVAFX_VERSION + "+";
    File javaRuntimeToUse;
    boolean javaRuntimeWasSet;

    BundleType bundleType = BundleType.NONE;
    String targetFormat; // null means any

    // list of jvm args (in theory string can contain spaces and need to be escaped)
    List<String> jvmargs = new LinkedList<>();
    Map<String, String> jvmUserArgs = new LinkedHashMap<>();

    // list of jvm properties (can also be passed as VM args)
    Map<String, String> properties = new LinkedHashMap<>();

    // raw arguments to the bundler
    Map<String, ? super Object> bundlerArguments = new LinkedHashMap<>();

    public void setId(String id) {
        this.id = id;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setServiceHint(Boolean serviceHint) {
        this.serviceHint = serviceHint;
    }

    public void setInstalldirChooser(Boolean installdirChooser) {
        this.installdirChooser = installdirChooser;
    }

    public void setSingleton(Boolean singleton) {
        this.singleton = singleton;
    }

    public void setSignBundle(Boolean signBundle) {
        this.signBundle = signBundle;
    }

    public void setJRE(String v) {
        jrePlatform = v;
    }

    public void setJavafx(String v) {
        fxPlatform = v;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setArguments(List<String> args) {
        this.arguments = args;
    }

    public void addAddModule(String value) {
        if (addModules == null) {
            addModules = value;
        } else {
            addModules += "," + value;
        }
    }

    public void addLimitModule(String value) {
        if (limitModules == null) {
            limitModules = value;
        } else {
            limitModules += "," + value;
        }
    }

    public void setModule(String value) {
        this.module = value;
    }

    public void setDebug(String value) {
        this.debugPort = value;
    }

    public void setStripNativeCommands(boolean value) {
        this.stripNativeCommands = value;
    }

    public void setDetectModules(boolean value) {
        this.detectmods = value;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setOutfile(String outfile) {
        this.outfile = outfile;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    public void setPreloader(String preloader) {
        this.preloader = preloader;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public void setExtension(boolean isExtension) {
        this.isExtension = isExtension;
    }

    public void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    // we need to expand as in some cases
    // (most notably javapackager)
    // we may get "." as filename and assumption is we include
    // everything in the given folder
    // (IOUtils.copyfiles() have recursive behavior)
    private List<File> expandFileset(File root) {
        List<File> files = new LinkedList<>();
        if (IOUtils.isNotSymbolicLink(root)) {
            if (root.isDirectory()) {
                File[] children = root.listFiles();
                if (children != null) {
                    for (File f : children) {
                        files.addAll(expandFileset(f));
                    }
                }
            } else {
                files.add(root);
            }
        }
        return files;
    }

    @Override
    public void addResource(File baseDir, String path) {
        File file = new File(baseDir, path);
        // normalize top level dir
        // to strip things like "." in the path
        // or it can confuse symlink detection logic
        file = file.getAbsoluteFile();

        if (baseDir == null) {
            baseDir = file.getParentFile();
        }
        resources.add(new RelativeFileSet(baseDir, new LinkedHashSet<>(expandFileset(file))));
    }

    @Override
    public void addResource(File baseDir, File file) {
        // normalize initial file
        // to strip things like "." in the path
        // or it can confuse symlink detection logic
        file = file.getAbsoluteFile();

        if (baseDir == null) {
            baseDir = file.getParentFile();
        }
        resources.add(new RelativeFileSet(baseDir, new LinkedHashSet<>(expandFileset(file))));
    }

    @Override
    public void validate() throws PackagerException {
        if (outdir == null) {
            throw new PackagerException("Error: Missing argument: {0}", "-outdir");
        }

        if (module == null) {
            if (resources.isEmpty()) {
                // throw new PackagerException("Error: Resources empty");
            }
            if (applicationClass == null) {
                throw new PackagerException("Error: Missing argument: {0}", "-appclass");
            }
        }
    }

    public boolean validateForBundle() {
        boolean result = false;

        // Success
        if (applicationClass != null && !applicationClass.isEmpty() ||
                module != null && !module.isEmpty()) {
            result = true;
        }

        return result;
    }

    public void setBundleType(BundleType type) {
        bundleType = type;
    }

    public BundleType getBundleType() {
        return bundleType;
    }

    public void setTargetFormat(String t) {
        targetFormat = t;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    private String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();

        if ("x86".equals(arch) || "i386".equals(arch) || "i486".equals(arch) ||
                "i586".equals(arch) || "i686".equals(arch)) {
            arch = "x86";
        } else if ("x86_64".equals(arch) || "amd64".equals(arch)) {
            arch = "x86_64";
        }

        return arch;
    }

    private static final Set<String> MULTI_ARGS = new TreeSet<>(Arrays.asList(
            StandardBundlerParam.JVM_PROPERTIES.getID(),
            StandardBundlerParam.JVM_OPTIONS.getID(),
            StandardBundlerParam.USER_JVM_OPTIONS.getID(),
            StandardBundlerParam.ARGUMENTS.getID(),
            StandardBundlerParam.MODULE_PATH.getID(),
            StandardBundlerParam.ADD_MODULES.getID(),
            StandardBundlerParam.LIMIT_MODULES.getID(),
            StandardBundlerParam.STRIP_NATIVE_COMMANDS.getID(),
            JLinkBundlerHelper.DETECT_MODULES.getID()));

    @SuppressWarnings("unchecked")
    public void addBundleArgument(String key, Object value) {
        // special hack for multi-line arguments
        if (MULTI_ARGS.contains(key) && value instanceof String) {
            Object existingValue = bundlerArguments.get(key);
            if (existingValue instanceof String) {
                bundlerArguments.put(key, existingValue + "\n\n" + value);
            } else if (existingValue instanceof List) {
                ((List) existingValue).add(value);
            } else if (existingValue instanceof Map && ((String) value).contains("=")) {
                String[] mapValues = ((String) value).split("=", 2);
                ((Map) existingValue).put(mapValues[0], mapValues[1]);
            } else {
                bundlerArguments.put(key, value);
            }
        } else {
            bundlerArguments.put(key, value);
        }
    }

    public BundleParams getBundleParams() {
        BundleParams bundleParams = new BundleParams();

        // construct app resources - relative to output folder!
        String currentOS = System.getProperty("os.name").toLowerCase();
        String currentArch = getArch();

        for (RelativeFileSet rfs : resources) {
            String os = rfs.getOs();
            String arch = rfs.getArch();
            // skip resources for other OS
            // and nativelib jars (we are including raw libraries)
            if ((os == null || currentOS.contains(os.toLowerCase())) &&
                    (arch == null || currentArch.startsWith(arch.toLowerCase())) &&
                    rfs.getType() != RelativeFileSet.Type.NATIVELIB) {
                if (rfs.getType() == RelativeFileSet.Type.LICENSE) {
                    for (String s : rfs.getIncludedFiles()) {
                        bundleParams.addLicenseFile(s);
                    }
                }
            }
        }

        bundleParams.setAppResourcesList(resources);

        bundleParams.setIdentifier(id);

        if (javaRuntimeWasSet) {
            bundleParams.setRuntime(javaRuntimeToUse);
        }
        bundleParams.setApplicationClass(applicationClass);
        bundleParams.setPrelaoderClass(preloader);
        bundleParams.setName(this.appName);
        bundleParams.setAppVersion(version);
        bundleParams.setType(bundleType);
        bundleParams.setBundleFormat(targetFormat);
        bundleParams.setVendor(vendor);
        bundleParams.setEmail(email);
        bundleParams.setShortcutHint(needShortcut);
        bundleParams.setMenuHint(needMenu);
        bundleParams.setSystemWide(systemWide);
        bundleParams.setServiceHint(serviceHint);
        bundleParams.setInstalldirChooser(installdirChooser);
        bundleParams.setSingleton(singleton);
        bundleParams.setSignBundle(signBundle);
        bundleParams.setCopyright(copyright);
        bundleParams.setApplicationCategory(category);
        bundleParams.setLicenseType(licenseType);
        bundleParams.setDescription(description);
        bundleParams.setTitle(title);
        if (verbose) {
            bundleParams.setVerbose(true);
        }

        bundleParams.setJvmProperties(properties);
        bundleParams.setJvmargs(jvmargs);
        bundleParams.setJvmUserArgs(jvmUserArgs);
        bundleParams.setArguments(arguments);

        if (addModules != null && !addModules.isEmpty()) {
            bundleParams.setAddModules(addModules);
        }

        if (limitModules != null && !limitModules.isEmpty()) {
            bundleParams.setLimitModules(limitModules);
        }

        if (stripNativeCommands != null) {
            bundleParams.setStripNativeCommands(stripNativeCommands);
        }

        bundleParams.setSrcDir(srcdir);

        if (modulePath != null && !modulePath.isEmpty()) {
            bundleParams.setModulePath(modulePath);
        }

        if (module != null && !module.isEmpty()) {
            bundleParams.setMainModule(module);
        }

        if (debugPort != null && !debugPort.isEmpty()) {
            bundleParams.setDebug(debugPort);
        }

        if (detectmods != null) {
            bundleParams.setDetectMods(detectmods);
        }

        // check for collisions
        TreeSet<String> keys = new TreeSet<>(bundlerArguments.keySet());
        keys.retainAll(bundleParams.getBundleParamsAsMap().keySet());

        if (!keys.isEmpty()) {
            throw new RuntimeException("Deploy Params and Bundler Arguments overlap in the following values:" +
                    keys.toString());
        }

        bundleParams.addAllBundleParams(bundlerArguments);

        return bundleParams;
    }
}
