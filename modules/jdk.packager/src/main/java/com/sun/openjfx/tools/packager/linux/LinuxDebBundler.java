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

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;
import com.sun.openjfx.tools.packager.bundlers.BundleParams;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.*;
import static com.sun.openjfx.tools.packager.linux.LinuxAppBundler.ICON_PNG;

public class LinuxDebBundler extends AbstractBundler {

    public static final BundlerParamInfo<LinuxAppBundler> APP_BUNDLER = new StandardBundlerParam<>(
            "",
            "",
            "linux.app.bundler",
            LinuxAppBundler.class,
            params -> new LinuxAppBundler(),
            (s, p) -> null);

    // Debian rules for package naming are used here
    // https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Source
    //
    // Package names must consist only of lower case letters (a-z), digits (0-9),
    // plus (+) and minus (-) signs, and periods (.).
    // They must be at least two characters long and must start with an alphanumeric character.
    //
    private static final Pattern DEB_BUNDLE_NAME_PATTERN =
            Pattern.compile("^[a-z][a-z\\d\\+\\-\\.]+");

    public static final BundlerParamInfo<String> BUNDLE_NAME = new StandardBundlerParam<> (
            "",
            "",
            "linux.bundleName",
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);

                if (nm == null) return null;

                // make sure to lower case and spaces/underscores become dashes
                nm = nm.toLowerCase().replaceAll("[ _]", "-");
                return nm;
            },
            (s, p) -> {
                if (!DEB_BUNDLE_NAME_PATTERN.matcher(s).matches()) {
                    throw new IllegalArgumentException(new ConfigException(
                            MessageFormat.format("Invalid value \"{0}\" for the package name.", s),
                            "Set the \"linux.bundleName\" parameter to a valid Debian " +
                                    "package name. Note that the package names must consist " +
                                    "only of lower case letters (a-z), digits (0-9), plus " +
                                    "(+) and minus (-) signs, and periods (.). They must be " +
                                    "at least two characters long and must start with an " +
                                    "alphanumeric character."));
                }

                return s;
            });

    public static final BundlerParamInfo<String> FULL_PACKAGE_NAME = new StandardBundlerParam<> (
            "",
            "",
            "linux.deb.fullPackageName",
            String.class,
            params -> BUNDLE_NAME.fetchFrom(params) + "-" + VERSION.fetchFrom(params),
            (s, p) -> s);

    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
            params ->  new File(BUILD_ROOT.fetchFrom(params), "linux"),
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> DEB_IMAGE_DIR = new StandardBundlerParam<>(
            "",
            "",
            "linux.deb.imageDir",
            File.class,
            params -> {
                File imagesRoot = IMAGES_ROOT.fetchFrom(params);
                if (!imagesRoot.exists()) imagesRoot.mkdirs();
                return new File(new File(imagesRoot, "linux-deb.image"), FULL_PACKAGE_NAME.fetchFrom(params));
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> APP_IMAGE_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "linux.deb.imageRoot",
            File.class,
            params -> {
                File imageDir = DEB_IMAGE_DIR.fetchFrom(params);
                return new File(imageDir, "opt");
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> CONFIG_DIR = new StandardBundlerParam<>(
            "",
            "",
            "linux.deb.configDir",
            File.class,
            params ->  new File(DEB_IMAGE_DIR.fetchFrom(params), "DEBIAN"),
            (s, p) -> new File(s));

    public static final BundlerParamInfo<String> EMAIL = new StandardBundlerParam<> (
            "",
            "",
            BundleParams.PARAM_EMAIL,
            String.class,
            params -> "Unknown",
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAINTAINER = new StandardBundlerParam<> (
            "",
            "",
            "linux.deb.maintainer",
            String.class,
            params -> VENDOR.fetchFrom(params) + " <" + EMAIL.fetchFrom(params) + ">",
            (s, p) -> s);

    public static final BundlerParamInfo<String> LICENSE_TEXT = new StandardBundlerParam<> (
            "",
            "",
            "linux.deb.licenseText",
            String.class,
            params -> {
                try {
                    List<String> licenseFiles = LICENSE_FILE.fetchFrom(params);

                    //need to copy license file to the root of linux-app.image
                    if (licenseFiles.size() > 0) {
                        String licFileStr = licenseFiles.get(0);

                        for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
                            if (rfs.contains(licFileStr)) {
                                return new String(IOUtils.readFully(new File(rfs.getBaseDirectory(), licFileStr)));
                            }
                        }
                    }
                } catch (Exception e) {
                    if (Log.isDebug()) {
                        e.printStackTrace();
                    }
                }
                return LICENSE_TYPE.fetchFrom(params);
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> XDG_FILE_PREFIX = new StandardBundlerParam<> (
            "Prefix for XDG files (mime, desktop)",
            "Prefix for XDG MimeInfo and Desktop Files.  Defaults to <vendor>-<appName>, with spaces dropped.",
            "linux.xdg-prefix",
            String.class,
            params -> {
                try {
                    String vendor;
                    if (params.containsKey(VENDOR.getID())) {
                        vendor = VENDOR.fetchFrom(params);
                    } else {
                        vendor = "javapackager";
                    }
                    String appName = APP_FS_NAME.fetchFrom(params);

                    return (vendor + "-" + appName).replaceAll("\\s", "");
                } catch (Exception e) {
                    if (Log.isDebug()) {
                        e.printStackTrace();
                    }
                }
                return "unknown-MimeInfo.xml";
            },
            (s, p) -> s);

    private final static String DEFAULT_ICON = "/packager/linux/javalogo_white_32.png";
    private final static String DEFAULT_CONTROL_TEMPLATE = "/packager/linux/template.control";
    private final static String DEFAULT_PRERM_TEMPLATE = "/packager/linux/template.prerm";
    private final static String DEFAULT_PREINSTALL_TEMPLATE = "/packager/linux/template.preinst";
    private final static String DEFAULT_POSTRM_TEMPLATE = "/packager/linux/template.postrm";
    private final static String DEFAULT_POSTINSTALL_TEMPLATE = "/packager/linux/template.postinst";
    private final static String DEFAULT_COPYRIGHT_TEMPLATE = "/packager/linux/template.copyright";
    private final static String DEFAULT_DESKTOP_FILE_TEMPLATE = "/packager/linux/template.desktop";
    private final static String DEFAULT_INIT_SCRIPT_TEMPLATE = "/packager/linux/template.deb.init.script";

    public final static String TOOL_DPKG = "dpkg-deb";

    public LinuxDebBundler() {
        super();
        baseResourceLoader = LinuxResources.class;
    }

    public static boolean testTool(String toolName, String minVersion) {
        try {
            ProcessBuilder pb = new ProcessBuilder(toolName, "--version");
            IOUtils.exec(pb, Log.isDebug(), true); //FIXME not interested in the output
        } catch (Exception e) {
            Log.verbose(MessageFormat.format("Test for [{0}]. Result: {1}", toolName, e.getMessage()));
            return false;
        }
        return true;
    }

    @Override
    public boolean validate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) throw new ConfigException(
                    "Parameters map is null.",
                    "Pass in a non-null parameters map.");

            //run basic validation to ensure requirements are met
            //we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(p).doValidate(p);

            //NOTE: Can we validate that the required tools are available before we start?
            if (!testTool(TOOL_DPKG, "1")){
                throw new ConfigException(
                        MessageFormat.format("Can not find {0}.", TOOL_DPKG),
                        "Please install required packages.");
            }


            // validate license file, if used, exists in the proper place
            if (p.containsKey(LICENSE_FILE.getID())) {
                List<RelativeFileSet> appResourcesList = APP_RESOURCES_LIST.fetchFrom(p);
                for (String license : LICENSE_FILE.fetchFrom(p)) {
                    boolean found = false;
                    for (RelativeFileSet appResources : appResourcesList) {
                        found = found || appResources.contains(license);
                    }
                    if (!found) {
                        throw new ConfigException(
                                "Specified license file is missing.",
                                MessageFormat.format("Make sure that \"{0}\" references a file in the app resources, " +
                                                "and that it is relative to the basedir \"{1}\".",
                                        license));
                    }
                }
            } else {
                Log.info("Debian packages should specify a license.  The absence of a license will cause some linux " +
                        "distributions to complain about the quality of the application.");
            }

            boolean serviceHint = p.containsKey(SERVICE_HINT.getID()) && SERVICE_HINT.fetchFrom(p);

            // for services, the app launcher must be less than 16 characters or init.d complains
            if (serviceHint && BUNDLE_NAME.fetchFrom(p).length() > 16) {
                throw new ConfigException(
                        MessageFormat.format("The bundle name \"{0}\" is too long for a daemon.", BUNDLE_NAME.fetchFrom(p)),
                        MessageFormat.format("Set a bundler argument \"{0}\" to a bundle name that is shorter than " +
                                "16 characters.", BUNDLE_NAME.getID()));
            }

            //treat default null as "system wide install"
            boolean systemWide = SYSTEM_WIDE.fetchFrom(p) == null || SYSTEM_WIDE.fetchFrom(p);

            if (serviceHint && !systemWide) {
                throw new ConfigException(
                        "Bundler doesn't support per-user daemons.",
                        "Make sure that the system wide hint is set to true.");
            }

            // only one mime type per association, at least one file extention
            List<Map<String, ? super Object>> associations = FILE_ASSOCIATIONS.fetchFrom(p);
            if (associations != null) {
                for (int i = 0; i < associations.size(); i++) {
                    Map<String, ? super Object> assoc = associations.get(i);
                    List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                    if (mimes == null || mimes.isEmpty()) {
                        throw new ConfigException(
                                MessageFormat.format("No MIME types were specified for File Association number {0}.", i),
                                "For Linux Bundling specify one and only one MIME type for each file association.");
                    } else if (mimes.size() > 1) {
                        throw new ConfigException(
                                MessageFormat.format("More than one MIME types was specified for File Association number {0}.", i),
                                "For Linux Bundling specify one and only one MIME type for each file association.");
                    }
                }
            }

            // bundle name has some restrictions
            // the string converter will throw an exception if invalid
            BUNDLE_NAME.getStringConverter().apply(BUNDLE_NAME.fetchFrom(p), p);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private boolean prepareProto(Map<String, ? super Object> p) {
        File appImageRoot = APP_IMAGE_ROOT.fetchFrom(p);
        File appDir = APP_BUNDLER.fetchFrom(p).doBundle(p, appImageRoot, true);
        return appDir != null;
    }

    //@Override
    public File bundle(Map<String, ? super Object> p, File outdir) {
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format(
                    "Output directory {0} cannot be created.", outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format(
                    "Output directory {0} is not writable.", outdir.getAbsolutePath()));
        }

        //we want to create following structure
        //   <package-name>
        //        DEBIAN
        //          control   (file with main package details)
        //          menu      (request to create menu)
        //          ... other control files if needed ....
        //        opt
        //          AppFolder (this is where app image goes)
        //             launcher executable
        //             app
        //             runtime

        File imageDir = DEB_IMAGE_DIR.fetchFrom(p);
        File configDir = CONFIG_DIR.fetchFrom(p);

        try {
            imageDir.mkdirs();
            configDir.mkdirs();
            if (prepareProto(p) && prepareProjectConfig(p)) {
                return buildDeb(p, outdir);
            }
            return null;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } finally {
            try {
                if (VERBOSE.fetchFrom(p)) {
                    saveConfigFiles(p);
                }
                if (imageDir != null && !Log.isDebug()) {
                    IOUtils.deleteRecursive(imageDir);
                } else if (imageDir != null) {
                    Log.info(MessageFormat.format("Kept working directory for debug: {0}", imageDir.getAbsolutePath()));
                }
            } catch (IOException ex) {
                //noinspection ReturnInsideFinallyBlock
                Log.debug(ex.getMessage());
                return null;
            }
        }
    }

    /*
     * Set permissions with a string like "rwxr-xr-x".
     */
    private void setPermissions(File file, String permissions) {
        Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString(permissions);
        try {
            if (file.exists()) {
                Files.setPosixFilePermissions(file.toPath(), filePermissions);
            }
        } catch (IOException ex) {
            Logger.getLogger(LinuxDebBundler.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void saveConfigFiles(Map<String, ? super Object> params) {
        try {
            File configRoot = CONFIG_ROOT.fetchFrom(params);
            File rootDir = LinuxAppBundler.getRootDir(APP_IMAGE_ROOT.fetchFrom(params), params);

            if (getConfig_ControlFile(params).exists()) {
                IOUtils.copyFile(getConfig_ControlFile(params),
                        new File(configRoot, getConfig_ControlFile(params).getName()));
            }
            if (getConfig_CopyrightFile(params).exists()) {
                IOUtils.copyFile(getConfig_CopyrightFile(params),
                        new File(configRoot, getConfig_CopyrightFile(params).getName()));
            }
            if (getConfig_PreinstallFile(params).exists()) {
                IOUtils.copyFile(getConfig_PreinstallFile(params),
                        new File(configRoot, getConfig_PreinstallFile(params).getName()));
            }
            if (getConfig_PrermFile(params).exists()) {
                IOUtils.copyFile(getConfig_PrermFile(params),
                        new File(configRoot, getConfig_PrermFile(params).getName()));
            }
            if (getConfig_PostinstallFile(params).exists()) {
                IOUtils.copyFile(getConfig_PostinstallFile(params),
                        new File(configRoot, getConfig_PostinstallFile(params).getName()));
            }
            if (getConfig_PostrmFile(params).exists()) {
                IOUtils.copyFile(getConfig_PostrmFile(params),
                        new File(configRoot, getConfig_PostrmFile(params).getName()));
            }
            if (getConfig_DesktopShortcutFile(rootDir, params).exists()) {
                IOUtils.copyFile(getConfig_DesktopShortcutFile(rootDir, params),
                        new File(configRoot, getConfig_DesktopShortcutFile(rootDir, params).getName()));
            }
            for (Map<String, ? super Object> secondaryLauncher : SECONDARY_LAUNCHERS.fetchFrom(params)) {
                if (getConfig_DesktopShortcutFile(rootDir, secondaryLauncher).exists()) {
                    IOUtils.copyFile(getConfig_DesktopShortcutFile(rootDir, secondaryLauncher),
                            new File(configRoot, getConfig_DesktopShortcutFile(rootDir, secondaryLauncher).getName()));
                }
            }
            if (getConfig_IconFile(rootDir, params).exists()) {
                IOUtils.copyFile(getConfig_IconFile(rootDir, params),
                        new File(configRoot, getConfig_IconFile(rootDir, params).getName()));
            }
            if (SERVICE_HINT.fetchFrom(params)) {
                if (getConfig_InitScriptFile(params).exists()) {
                    IOUtils.copyFile(getConfig_InitScriptFile(params),
                            new File(configRoot, getConfig_InitScriptFile(params).getName()));
                }
            }
            Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                    configRoot.getAbsolutePath()));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private String getArch() {
        String arch = System.getProperty("os.arch");
        if ("i386".equals(arch)) {
            return "i386";
        } else {
            return "amd64";
        }
    }

    private long getInstalledSizeKB(Map<String, ? super Object> params) {
        return getInstalledSizeKB(APP_IMAGE_ROOT.fetchFrom(params)) >> 10;
    }

    private long getInstalledSizeKB(File dir) {
        long count = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File file : children) {
                if (file.isFile()) {
                    count += file.length();
                }
                else if (file.isDirectory()) {
                    count += getInstalledSizeKB(file);
                }
            }
        }
        return count;
    }

    private boolean prepareProjectConfig(Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = createReplacementData(params);
        File rootDir = LinuxAppBundler.getRootDir(APP_IMAGE_ROOT.fetchFrom(params), params);
        // prepare installer icon
        File iconTarget = getConfig_IconFile(rootDir, params);
        File icon = ICON_PNG.fetchFrom(params);
        if (icon == null || !icon.exists()) {
            fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                    "menu icon",
                    DEFAULT_ICON,
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        } else {
            fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                    "menu icon",
                    icon,
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        }

        StringBuilder installScripts = new StringBuilder();
        StringBuilder removeScripts = new StringBuilder();
        for (Map<String, ? super Object> secondaryLauncher : SECONDARY_LAUNCHERS.fetchFrom(params)) {
            Map<String, String> secondaryLauncherData = createReplacementData(secondaryLauncher);
            secondaryLauncherData.put("APPLICATION_FS_NAME", data.get("APPLICATION_FS_NAME"));
            secondaryLauncherData.put("DESKTOP_MIMES", "");

            // prepare desktop shortcut
            Writer w = new BufferedWriter(new FileWriter(getConfig_DesktopShortcutFile(rootDir, secondaryLauncher)));
            String content = preprocessTextResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX +
                            getConfig_DesktopShortcutFile(rootDir, secondaryLauncher).getName(),
                    "Menu shortcut descriptor",
                    DEFAULT_DESKTOP_FILE_TEMPLATE,
                    secondaryLauncherData,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            w.write(content);
            w.close();

            // prepare installer icon
            iconTarget = getConfig_IconFile(rootDir, secondaryLauncher);
            icon = ICON_PNG.fetchFrom(secondaryLauncher);
            if (icon == null || !icon.exists()) {
                fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                        "menu icon",
                        DEFAULT_ICON,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            } else {
                fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                        "menu icon",
                        icon,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            }

            // postinst copying of desktop icon
            installScripts.append("        xdg-desktop-menu install --novendor /opt/");
            installScripts.append(data.get("APPLICATION_FS_NAME"));
            installScripts.append("/");
            installScripts.append(secondaryLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            installScripts.append(".desktop\n");

            // postrm cleanup of desktop icon
            removeScripts.append("        xdg-desktop-menu uninstall --novendor /opt/");
            removeScripts.append(data.get("APPLICATION_FS_NAME"));
            removeScripts.append("/");
            removeScripts.append(secondaryLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            removeScripts.append(".desktop\n");
        }
        data.put("SECONDARY_LAUNCHERS_INSTALL", installScripts.toString());
        data.put("SECONDARY_LAUNCHERS_REMOVE", removeScripts.toString());

        StringBuilder cdsScript = new StringBuilder();
        if (ENABLE_APP_CDS.fetchFrom(params)
                && ("install".equals(APP_CDS_CACHE_MODE.fetchFrom(params))
                || "auto+install".equals(APP_CDS_CACHE_MODE.fetchFrom(params)))) {
            cdsScript.append("/opt/");
            cdsScript.append(data.get("APPLICATION_FS_NAME"));
            cdsScript.append("/");
            cdsScript.append(data.get("APPLICATION_LAUNCHER_FILENAME"));
            cdsScript.append(" -Xappcds:generatecache\n");
        }

        data.put("APP_CDS_CACHE", cdsScript.toString());

        List<Map<String, ? super Object>> associations = FILE_ASSOCIATIONS.fetchFrom(params);
        data.put("FILE_ASSOCIATION_INSTALL", "");
        data.put("FILE_ASSOCIATION_REMOVE", "");
        data.put("DESKTOP_MIMES", "");
        if (associations != null) {
            String mimeInfoFile = XDG_FILE_PREFIX.fetchFrom(params) + "-MimeInfo.xml";
            StringBuilder mimeInfo = new StringBuilder("<?xml version=\"1.0\"?>\n<mime-info xmlns='" +
                    "http://www.freedesktop.org/standards/shared-mime-info'>\n");
            StringBuilder registrations = new StringBuilder();
            StringBuilder deregistrations = new StringBuilder();
            StringBuilder desktopMimes = new StringBuilder("MimeType=");
            boolean addedEntry = false;

            for (Map<String, ? super Object> assoc : associations) {
                //  <mime-type type="application/x-vnd.awesome">
                //    <comment>Awesome document</comment>
                //    <glob pattern="*.awesome"/>
                //    <glob pattern="*.awe"/>
                //  </mime-type>

                if (assoc == null) {
                    continue;
                }

                String description = FA_DESCRIPTION.fetchFrom(assoc);
                File faIcon = FA_ICON.fetchFrom(assoc); // TODO FA_ICON_PNG
                List<String> extensions = FA_EXTENSIONS.fetchFrom(assoc);
                if (extensions == null) {
                    Log.info("Creating association with null extension.");
                }

                List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                if (mimes == null || mimes.isEmpty()) {
                    continue;
                }
                String thisMime = mimes.get(0);
                String dashMime = thisMime.replace('/', '-');

                mimeInfo.append("  <mime-type type='")
                        .append(thisMime)
                        .append("'>\n");
                if (description != null && !description.isEmpty()) {
                    mimeInfo.append("    <comment>")
                            .append(description)
                            .append("</comment>\n");
                }

                if (extensions != null) {
                    for (String ext : extensions) {
                        mimeInfo.append("    <glob pattern='*.")
                                .append(ext)
                                .append("'/>\n");
                    }
                }

                mimeInfo.append("  </mime-type>\n");
                if (!addedEntry) {
                    registrations.append("        xdg-mime install /opt/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");

                    deregistrations.append("        xdg-mime uninstall /opt/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");
                    addedEntry = true;
                } else {
                    desktopMimes.append(";");
                }
                desktopMimes.append(thisMime);

                if (faIcon != null && faIcon.exists()) {
                    int size = getSquareSizeOfImage(faIcon);

                    if (size > 0) {
                        File target = new File(rootDir, APP_FS_NAME.fetchFrom(params) + "_fa_" + faIcon.getName());
                        IOUtils.copyFile(faIcon, target);

                        //xdg-icon-resource install --context mimetypes --size 64 awesomeapp_fa_1.png application-x.vnd-awesome
                        registrations.append("        xdg-icon-resource install --context mimetypes --size ")
                                .append(size)
                                .append(" /opt/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");

                        //xdg-icon-resource uninstall --context mimetypes --size 64 awesomeapp_fa_1.png application-x.vnd-awesome
                        deregistrations.append("        xdg-icon-resource uninstall --context mimetypes --size ")
                                .append(size)
                                .append(" /opt/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");
                    }
                }
            }
            mimeInfo.append("</mime-info>");

            if (addedEntry) {
                Writer writer = new BufferedWriter(new FileWriter(new File(rootDir, mimeInfoFile)));
                writer.write(mimeInfo.toString());
                writer.close();
                data.put("FILE_ASSOCIATION_INSTALL", registrations.toString());
                data.put("FILE_ASSOCIATION_REMOVE", deregistrations.toString());
                data.put("DESKTOP_MIMES", desktopMimes.toString());
            }
        }

        // prepare desktop shortcut
        Writer writer = new BufferedWriter(new FileWriter(getConfig_DesktopShortcutFile(rootDir, params)));
        String content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_DesktopShortcutFile(rootDir, params).getName(),
                "Menu shortcut descriptor",
                DEFAULT_DESKTOP_FILE_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();

        // prepare control file
        writer = new BufferedWriter(new FileWriter(getConfig_ControlFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_ControlFile(params).getName(),
                "DEB control file",
                DEFAULT_CONTROL_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();

        writer = new BufferedWriter(new FileWriter(getConfig_PreinstallFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_PreinstallFile(params).getName(),
                "DEB preinstall script",
                DEFAULT_PREINSTALL_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();
        setPermissions(getConfig_PreinstallFile(params), "rwxr-xr-x");

        writer = new BufferedWriter(new FileWriter(getConfig_PrermFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_PrermFile(params).getName(),
                "DEB prerm script",
                DEFAULT_PRERM_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();
        setPermissions(getConfig_PrermFile(params), "rwxr-xr-x");

        writer = new BufferedWriter(new FileWriter(getConfig_PostinstallFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_PostinstallFile(params).getName(),
                "DEB postinstall script",
                DEFAULT_POSTINSTALL_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();
        setPermissions(getConfig_PostinstallFile(params), "rwxr-xr-x");

        writer = new BufferedWriter(new FileWriter(getConfig_PostrmFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_PostrmFile(params).getName(),
                "DEB postrm script",
                DEFAULT_POSTRM_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();
        setPermissions(getConfig_PostrmFile(params), "rwxr-xr-x");

        writer = new BufferedWriter(new FileWriter(getConfig_CopyrightFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_CopyrightFile(params).getName(),
                "DEB copyright file",
                DEFAULT_COPYRIGHT_TEMPLATE,
                data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        writer.write(content);
        writer.close();

        if (SERVICE_HINT.fetchFrom(params)) {
            //prepare init script
            writer = new BufferedWriter(new FileWriter(getConfig_InitScriptFile(params)));
            content = preprocessTextResource(
                    LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_InitScriptFile(params).getName(),
                    "DEB init script",
                    DEFAULT_INIT_SCRIPT_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            writer.write(content);
            writer.close();
            setPermissions(getConfig_InitScriptFile(params), "rwxr-xr-x");
        }

        return true;
    }

    private Map<String, String> createReplacementData(Map<String, ? super Object> params) {
        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_FS_NAME", APP_FS_NAME.fetchFrom(params));
        data.put("APPLICATION_PACKAGE", BUNDLE_NAME.fetchFrom(params));
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_MAINTAINER", MAINTAINER.fetchFrom(params));
        data.put("APPLICATION_VERSION", VERSION.fetchFrom(params));
        data.put("APPLICATION_LAUNCHER_FILENAME", APP_FS_NAME.fetchFrom(params));
        data.put("XDG_PREFIX", XDG_FILE_PREFIX.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_CATEGORY", CATEGORY.fetchFrom(params));
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_SUMMARY", TITLE.fetchFrom(params));
        data.put("APPLICATION_COPYRIGHT", COPYRIGHT.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TYPE", LICENSE_TYPE.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TEXT", LICENSE_TEXT.fetchFrom(params));
        data.put("APPLICATION_ARCH", getArch());
        data.put("APPLICATION_INSTALLED_SIZE", Long.toString(getInstalledSizeKB(params)));
        data.put("SERVICE_HINT", String.valueOf(SERVICE_HINT.fetchFrom(params)));
        data.put("START_ON_INSTALL", String.valueOf(START_ON_INSTALL.fetchFrom(params)));
        data.put("STOP_ON_UNINSTALL", String.valueOf(STOP_ON_UNINSTALL.fetchFrom(params)));
        data.put("RUN_AT_STARTUP", String.valueOf(RUN_AT_STARTUP.fetchFrom(params)));
        return data;
    }

    private File getConfig_DesktopShortcutFile(File rootDir, Map<String, ? super Object> params) {
        return new File(rootDir, APP_FS_NAME.fetchFrom(params) + ".desktop");
    }

    private File getConfig_IconFile(File rootDir, Map<String, ? super Object> params) {
        return new File(rootDir, APP_FS_NAME.fetchFrom(params) + ".png");
    }

    private File getConfig_InitScriptFile(Map<String, ? super Object> params) {
        return new File(LinuxAppBundler.getRootDir(APP_IMAGE_ROOT.fetchFrom(params), params),
                BUNDLE_NAME.fetchFrom(params) + ".init");
    }

    private File getConfig_ControlFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "control");
    }

    private File getConfig_PreinstallFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "preinst");
    }

    private File getConfig_PrermFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "prerm");
    }

    private File getConfig_PostinstallFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "postinst");
    }

    private File getConfig_PostrmFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "postrm");
    }

    private File getConfig_CopyrightFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "copyright");
    }

    private File buildDeb(Map<String, ? super Object> params, File outdir) throws IOException {
        File outFile = new File(outdir, FULL_PACKAGE_NAME.fetchFrom(params) + ".deb");
        Log.verbose(MessageFormat.format("Generating DEB for installer to: {0}", outFile.getAbsolutePath()));

        outFile.getParentFile().mkdirs();

        // run dpkg
        ProcessBuilder pb = new ProcessBuilder(
                "fakeroot", TOOL_DPKG, "-b",  FULL_PACKAGE_NAME.fetchFrom(params),
                outFile.getAbsolutePath());
        pb = pb.directory(DEB_IMAGE_DIR.fetchFrom(params).getParentFile());
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));

        Log.info(MessageFormat.format("Package (.deb) saved to: {0}", outFile.getAbsolutePath()));

        return outFile;
    }

    @Override
    public String getName() {
        return "Linux DEB Installer";
    }

    @Override
    public String getDescription() {
        return "Linux Debian Bundle.";
    }

    @Override
    public String getID() {
        return "deb";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(LinuxAppBundler.getAppBundleParameters());
        results.addAll(getDebBundleParameters());
        return results;
    }

    private static Collection<BundlerParamInfo<?>> getDebBundleParameters() {
        return Arrays.asList(BUNDLE_NAME,
                COPYRIGHT,
                CATEGORY,
                DESCRIPTION,
                // DROP_IN_RESOURCES_ROOT,
                EMAIL,
                ICON_PNG,
                LICENSE_FILE,
                LICENSE_TYPE,
                TITLE,
                VENDOR);
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }

    private int getSquareSizeOfImage(File f) {
        try {
            BufferedImage bufferedImage = ImageIO.read(f);
            if (bufferedImage.getWidth() == bufferedImage.getHeight()) {
                return bufferedImage.getWidth();
            } else {
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
