/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.MessageFormat;import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.StandardBundlerParam;

import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

public class MacDaemonBundler extends AbstractBundler {

    private static final String TEMPLATE_LAUNCHD_PLIST  = "launchd.plist.template";

    public final static String MAC_BUNDLER_PREFIX =
            BUNDLER_PREFIX + "macosx" + File.separator;

    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
            params -> {
                File configRoot = new File(BUILD_ROOT.fetchFrom(params), "macosx");
                configRoot.mkdirs();
                return configRoot;
            },
            (s, p) -> new File(s));

    public MacDaemonBundler() {
        super();
        baseResourceLoader = MacResources.class;
    }

    private File getConfig_LaunchdPlist(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), "launchd.plist");
    }

    private void prepareConfigFiles(Map<String, ? super Object> params) throws IOException {
        File launchdPlistFile = getConfig_LaunchdPlist(params);
        launchdPlistFile.createNewFile();
        writeLaunchdPlist(launchdPlistFile, params);
    }

    private String getDaemonIdentifier(Map<String, ? super Object> params) {
        return IDENTIFIER.fetchFrom(params).toLowerCase() + ".daemon";
    }

    public String getAppName(Map<String, ? super Object> params) {
        return APP_NAME.fetchFrom(params) + ".app";
    }

    private String getLauncherName(Map<String, ? super Object> params) {
        if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            return MAIN_CLASS.fetchFrom(params);
        }
    }

    private String getDaemonLauncherPath(Map<String, ? super Object> params) {
        return "/Applications/" + getAppName(params) +
                "/Contents/MacOS/" + getLauncherName(params);
    }

    private void writeLaunchdPlist(File file, Map<String, ? super Object> params)
            throws IOException
    {
        Log.verbose(MessageFormat.format("Preparing launchd.plist: {0}", file.getAbsolutePath()));

        Map<String, String> data = new HashMap<>();

        data.put("DEPLOY_DAEMON_IDENTIFIER", getDaemonIdentifier(params));
        data.put("DEPLOY_DAEMON_LAUNCHER_PATH", getDaemonLauncherPath(params));
        data.put("DEPLOY_RUN_AT_LOAD", String.valueOf((START_ON_INSTALL.fetchFrom(params))));
        data.put("DEPLOY_KEEP_ALIVE", String.valueOf((RUN_AT_STARTUP.fetchFrom(params))));

        Writer w = new BufferedWriter(new FileWriter(file));
        w.write(preprocessTextResource(
                MAC_BUNDLER_PREFIX + getConfig_LaunchdPlist(params).getName(),
                "Bundle launchd config file", TEMPLATE_LAUNCHD_PLIST, data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params)));
        w.close();
    }

    protected void cleanupConfigFiles(Map<String, ? super Object> params) {
        if (CONFIG_ROOT.fetchFrom(params) != null) {
            if (getConfig_LaunchdPlist(params) != null) {
                getConfig_LaunchdPlist(params).delete();
            }
        }
    }

    /*
     * Creates the following structure
     *
     *  <package-name>
     *      Library
     *          LaunchDaemons
     *              plist file
     */
    public File doBundle(Map<String, ? super Object> params, File outputDirectory, boolean dependentTask) {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outputDirectory.getAbsolutePath()));
        }
        if (!outputDirectory.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outputDirectory.getAbsolutePath()));
        }

        File rootDirectory = null;

        try {
            //prepare config resources (we will copy them to the bundle later)
            // NB: explicitly saving them to simplify customization
            prepareConfigFiles(params);

            // Create directory structure
            rootDirectory = new File(outputDirectory, APP_NAME.fetchFrom(params) + ".daemon");
            IOUtils.deleteRecursive(rootDirectory);
            rootDirectory.mkdirs();

            if (!dependentTask) {
                Log.info(MessageFormat.format("Creating daemon component: {0}", rootDirectory.getAbsolutePath()));
            }

            File libraryDirectory = new File(rootDirectory, "Library");
            libraryDirectory.mkdirs();

            File launchDaemonsDirectory = new File(libraryDirectory, "LaunchDaemons");
            launchDaemonsDirectory.mkdirs();

            // Generate launchd.plist
            IOUtils.copyFile(getConfig_LaunchdPlist(params),
                    new File(launchDaemonsDirectory,
                            IDENTIFIER.fetchFrom(params).toLowerCase() + ".launchd.plist"));

        } catch(IOException ex) {
            Log.verbose(ex);
            return null;
        } finally {
            if (!VERBOSE.fetchFrom(params)) {
                //cleanup
                cleanupConfigFiles(params);
            } else {
                Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                        CONFIG_ROOT.fetchFrom(params).getAbsolutePath()));
            }
        }

        return rootDirectory;
    }

    @Override
    public String getName() {
        return "Mac Daemon Component";
    }

    @Override
    public String getDescription() {
        return "Mac Daemon Component - contains configuration files describing daemons.";
    }

    @Override
    public String getID() {
        return "mac.daemon";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        return getDaemonBundleParameters();
    }

    public static Collection<BundlerParamInfo<?>> getDaemonBundleParameters() {
        return Arrays.asList(APP_NAME,
                BUILD_ROOT,
                IDENTIFIER,
                START_ON_INSTALL,
                RUN_AT_STARTUP);
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws UnsupportedPlatformException, ConfigException
    {
        try {
            return doValidate(params);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }

    }

    public boolean doValidate(Map<String, ? super Object> p)
            throws UnsupportedPlatformException, ConfigException
    {
        if (Platform.getPlatform() != Platform.MAC) {
            throw new UnsupportedPlatformException();
        }

        //treat default null as "system wide install"
        boolean systemWide = SYSTEM_WIDE.fetchFrom(p) == null || SYSTEM_WIDE.fetchFrom(p);

        if (!systemWide) {
            throw new ConfigException("Bundler doesn't support per-user daemons.",
                    "Make sure that the system wide hint is set to true.");
        }

        return true;
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }

}
