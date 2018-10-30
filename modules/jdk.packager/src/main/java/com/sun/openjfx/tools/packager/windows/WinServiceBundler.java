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

package com.sun.openjfx.tools.packager.windows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SYSTEM_WIDE;

public class WinServiceBundler extends AbstractBundler {

    private static final String EXECUTABLE_SVC_NAME = "WinLauncherSvc.exe";

    public WinServiceBundler() {
        super();
    }

    @Override
    public String getName() {
        return "Windows Service Component";
    }

    @Override
    public String getDescription() {
        return "Windows Service Component - contains native launcher for service app.";
    }

    @Override
    public String getID() {
        return "windows.service";
    }

    @Override
    public String getBundleType() {
        return "IMAGE";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        return getServiceBundleParameters();
    }

    private static Collection<BundlerParamInfo<?>> getServiceBundleParameters() {
        return Arrays.asList(APP_NAME, BUILD_ROOT);
    }

    @Override
    public boolean validate(Map<String, ? super Object> params) throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) {
                throw new ConfigException("Parameters map is null.", "Pass in a non-null parameters map.");
            }

            return doValidate(params);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private boolean doValidate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.WINDOWS) {
            throw new UnsupportedPlatformException();
        }

        Path winSvcPath = Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows",
                EXECUTABLE_SVC_NAME);
        if (!Files.exists(winSvcPath)) {
            throw new ConfigException(EXECUTABLE_SVC_NAME + " not found (\"" + winSvcPath.toAbsolutePath() + "\").",
                    "Do something else :).");
        }

        // treat default null as "system wide install"
        boolean systemWide = SYSTEM_WIDE.fetchFrom(p) == null || SYSTEM_WIDE.fetchFrom(p);
        if (!systemWide) {
            throw new ConfigException("Bundler doesn't support per-user services.",
                    "Make sure that the system wide hint is set to true.");
        }

        return true;
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }

    private static String getAppName(Map<String, ? super Object> p) {
        return APP_FS_NAME.fetchFrom(p);
    }

    static String getAppSvcName(Map<String, ? super Object>  p) {
        return APP_FS_NAME.fetchFrom(p) + "Svc";
    }

    public static File getLauncherSvc(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, getAppName(p) + "Svc.exe");
    }

    /*
     * Copies the service launcher to the output folder
     *
     * Note that the bundler doesn't create folder structure and
     * just copies the launcher to the output folder
     */
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
                Log.info(MessageFormat.format("Creating service bundle: {0} in {1}", getAppSvcName(p),
                        outputDirectory.getAbsolutePath()));
            }

            // Copy executable to install application as service
            File executableSvcFile = getLauncherSvc(outputDirectory, p);
            Path winSvcPath = Paths.get("./build/generated-resources/com/sun/openjfx/tools/packager/windows",
                    EXECUTABLE_SVC_NAME);
            IOUtils.copyFromURL(winSvcPath.toUri().toURL(), executableSvcFile);
            executableSvcFile.setExecutable(true, false);

            if (!dependentTask) {
                Log.info(MessageFormat.format("Result service bundle: {0}", outputDirectory.getAbsolutePath()));
            }

            return outputDirectory;
        } catch (IOException ex) {
            Log.info("Exception: " + ex);
            Log.debug(ex);
            return null;
        }

    }
}
