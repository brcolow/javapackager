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

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;

public abstract class MacBaseInstallerBundler extends AbstractBundler {

    //This could be generalized more to be for any type of Image Bundler
    public static final BundlerParamInfo<MacAppBundler> APP_BUNDLER = new StandardBundlerParam<>(
            "Mac App Bundler",
            "Creates a .app bundle for the Mac",
            "mac.app.bundler",
            MacAppBundler.class,
            params -> new MacAppBundler(),
            (s, p) -> null);

    public final BundlerParamInfo<File> APP_IMAGE_BUILD_ROOT = new StandardBundlerParam<>(
            "",
            "This is temporary location built by the packager that is the root of the image application",
            "mac.app.imageRoot",
            File.class,
            params -> {
                File imageDir = IMAGES_ROOT.fetchFrom(params);
                if (!imageDir.exists()) imageDir.mkdirs();
                try {
                    return Files.createTempDirectory(imageDir.toPath(), "image-").toFile();
                } catch (IOException e) {
                    return new File(imageDir, getID()+ ".image");
                }
            },
            (s, p) -> new File(s));

    public static final StandardBundlerParam<File> MAC_APP_IMAGE = new StandardBundlerParam<>(
            "Image Directory",
            "Location of the image that will be used to build either a DMG or PKG installer.",
            "mac.app.image",
            File.class,
            params -> null,
            (s, p) -> new File(s));


    public static final BundlerParamInfo<MacDaemonBundler> DAEMON_BUNDLER = new StandardBundlerParam<>(
            "Mac Daemon Bundler",
            "Creates daemon image for the Mac",
            "mac.daemon.bundler",
            MacDaemonBundler.class,
            params -> new MacDaemonBundler(),
            (s, p) -> null);


    public final BundlerParamInfo<File> DAEMON_IMAGE_BUILD_ROOT = new StandardBundlerParam<>(
            "",
            "This is temporary location built by the packager that is the root of the daemon image.",
            "mac.daemon.image",
            File.class,
            params -> {
                File imageDir = IMAGES_ROOT.fetchFrom(params);
                if (!imageDir.exists()) imageDir.mkdirs();
                return new File(imageDir, getID()+ ".daemon");
            },
            (s, p) -> new File(s));


    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
            params -> {
                File imagesRoot = new File(BUILD_ROOT.fetchFrom(params), "macosx");
                imagesRoot.mkdirs();
                return imagesRoot;
            },
            (s, p) -> null);

    public static final BundlerParamInfo<String> SIGNING_KEY_USER = new StandardBundlerParam<>(
            "Signing Key User Name",
            "The user name portion of the typical \"Mac Developer ID Application: <user name>\" signing key.",
            "mac.signing-key-user-name",
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> SIGNING_KEYCHAIN = new StandardBundlerParam<>(
            "Signing Keychain",
            "The location of the keychain to use.  If not specified the standard keychains will be used.",
            "mac.signing-keychain",
            String.class,
            params -> "",
            null);

    public static final BundlerParamInfo<String> INSTALLER_NAME = new StandardBundlerParam<> (
            "Installer Name",
            "The filename of the generated installer without the file type extension.  Default is <App Name>-<Version>.",
            "mac.installerName",
            String.class,
            params -> {
                String nm = APP_FS_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

    public static File getPredefinedImage(Map<String, ? super Object> p) {
        File applicationImage = null;
        if (MAC_APP_IMAGE.fetchFrom(p) != null) {
            applicationImage = MAC_APP_IMAGE.fetchFrom(p);
            Log.debug("Using App Image from " + applicationImage);
            if (!applicationImage.exists()) {
                throw new RuntimeException(
                        MessageFormat.format("Specified image directory {0}: {1} does not exists",
                                MAC_APP_IMAGE.getID(), applicationImage.toString()));
            }
        }
        return applicationImage;
    }

    protected void validateAppImageAndBundeler(Map<String, ? super Object> params) throws ConfigException, UnsupportedPlatformException {
        if (MAC_APP_IMAGE.fetchFrom(params) != null) {
            File applicationImage = MAC_APP_IMAGE.fetchFrom(params);
            if (!applicationImage.exists()) {
                throw new ConfigException(
                        MessageFormat.format("Specified image directory {0}: {1} does not exists",
                                MAC_APP_IMAGE.getID(), applicationImage.toString()),
                        MessageFormat.format("Confirm that the value for \"{0}\" exists", MAC_APP_IMAGE.getID()));
            }
            if (APP_NAME.fetchFrom(params) == null) {
                throw new ConfigException(
                        "When using an external app image you must specify the app name.",
                        "Set the app name via the -name CLI flag, the fx:application/@name ANT attribute, or via the " +
                                "'appName' bundler argument.");
            }
            if (IDENTIFIER.fetchFrom(params) == null) {
                throw new ConfigException("When using an external app image you must specify the identifier.",
                        "Set the identifier via the -appId CLI flag, the fx:application/@id ANT attribute, or via " +
                                "the 'identifier' bundler argument.");
            }
        } else {
            APP_BUNDLER.fetchFrom(params).doValidate(params);
        }
    }

    protected File prepareAppBundle(Map<String, ? super Object> p) {
        File predefinedImage = getPredefinedImage(p);
        if (predefinedImage != null) {
            return predefinedImage;
        }

        File appImageRoot = APP_IMAGE_BUILD_ROOT.fetchFrom(p);
        return APP_BUNDLER.fetchFrom(p).doBundle(p, appImageRoot, true);
    }

    protected File prepareDaemonBundle(Map<String, ? super Object> p) {
        File daemonImageRoot = DAEMON_IMAGE_BUILD_ROOT.fetchFrom(p);
        return DAEMON_BUNDLER.fetchFrom(p).doBundle(p, daemonImageRoot, true);
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();

        results.addAll(MacAppBundler.getAppBundleParameters());
        results.addAll(Arrays.asList(
                APP_BUNDLER,
                CONFIG_ROOT,
                APP_IMAGE_BUILD_ROOT,
                MAC_APP_IMAGE
        ));

        return results;
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    public static String findKey(String key, String keychainName, boolean verbose) {
        if (Platform.getPlatform() != Platform.MAC) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos)) {
            List<String> searchOptions = new ArrayList<>();
            searchOptions.add("security");
            searchOptions.add("find-certificate");
            searchOptions.add("-c");
            searchOptions.add(key);
            searchOptions.add("-a");
            if (keychainName != null && !keychainName.isEmpty()) {
                // searchOptions.add(keychainName);
            }

            ProcessBuilder pb = new ProcessBuilder(searchOptions);
            IOUtils.exec(pb, verbose, false, ps);

            System.out.println("Inside MacBaseInstallerBundler#findKey");
            System.out.println("baos.toString(): " + baos.toString());
            Pattern p = Pattern.compile("\"alis\"<blob>=\"([^\"]+)\"");
            Matcher m = p.matcher(baos.toString());
            if (!m.find()) {
                Log.info("Did not find a key matching '" + key + "'");
                return null;
            }
            String matchedKey = m.group(1);
            if (m.find()) {
                Log.info("Found more than one key matching '"  + key + "'");
                return null;
            }
            Log.debug("Using key '" + matchedKey + "'");
            return matchedKey;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Log.verbose(ioe);
            return null;
        }
    }
}
