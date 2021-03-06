/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.Bundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.BundlersTest;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ARGUMENTS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CATEGORY;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CLASSPATH;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.COPYRIGHT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ENABLE_APP_CDS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_CONTENT_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_EXTENSIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FILE_ASSOCIATIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.IDENTIFIER;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_PROPERTIES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_JAR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MODULE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PREFERENCES_ID;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PRELOADER_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.RUN_AT_STARTUP;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SECONDARY_LAUNCHERS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SERVICE_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.START_ON_INSTALL;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.STOP_ON_UNINSTALL;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SYSTEM_WIDE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.TITLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.USER_JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VENDOR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;
import static com.sun.openjfx.tools.packager.linux.LinuxAppBundler.ICON_PNG;
import static com.sun.openjfx.tools.packager.linux.LinuxAppBundler.LINUX_RUNTIME;
import static com.sun.openjfx.tools.packager.linux.LinuxDebBundler.BUNDLE_NAME;
import static com.sun.openjfx.tools.packager.linux.LinuxDebBundler.EMAIL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class LinuxDebBundlerTest {

    static File tmpBase;
    static File workDir;
    static File appResourcesDir;
    static File fakeMainJar;
    static String runtimeJdk;
    static String runtimeJre;
    static Set<File> appResources;
    static boolean retain = false;

    @BeforeClass
    public static void prepareApp() {
        // only run on linux
        Assume.assumeTrue(Platform.getPlatform() == Platform.LINUX);

        runtimeJdk = System.getenv("PACKAGER_JDK_ROOT");
        runtimeJre = System.getenv("PACKAGER_JRE_ROOT");

        assumeTrue("dpkg was not found - skipping PKG tests",
                LinuxDebBundler.testTool(LinuxDebBundler.TOOL_DPKG));

        Log.setLogger(new Log.Logger(true));
        Log.setDebug(true);

        retain = Boolean.parseBoolean(System.getProperty("RETAIN_PACKAGER_TESTS"));

        workDir = Paths.get("./build/tmp/tests", "linuxdeb").toFile();
        appResourcesDir = Paths.get("./build/tmp/tests/appResources").toFile();
        fakeMainJar = new File("./build/tmp/tests/appResources", "mainApp.jar");
        appResources = new HashSet<>(Arrays.asList(fakeMainJar,
                new File(appResourcesDir, "LICENSE"),
                new File(appResourcesDir, "LICENSE2")));
    }

    @Before
    public void createTmpDir() {
        if (retain) {
            tmpBase = Paths.get("./build/tmp/tests/linuxdeb").toFile();
        } else {
            tmpBase = BUILD_ROOT.fetchFrom(new TreeMap<>());
        }
        tmpBase.mkdir();
    }

    @After
    public void maybeCleanupTmpDir() {
        if (!retain) {
            attemptDelete(tmpBase);
        }
    }

    private void attemptDelete(File tmpBase) {
        if (tmpBase.isDirectory()) {
            File[] children = tmpBase.listFiles();
            if (children != null) {
                for (File f : children) {
                    attemptDelete(f);
                }
            }
        }
        boolean success;
        try {
            success = !tmpBase.exists() || tmpBase.delete();
        } catch (SecurityException se) {
            success = false;
        }
        if (!success) {
            System.err.println("Could not clean up " + tmpBase.toString());
        }
    }

    @Test
    public void testAppNameForDebBundler() {
        // valid names for deb package
        BundlersTest.testValidValueForBaseParam(APP_NAME, "test", BUNDLE_NAME);
        BundlersTest.testValidValueForBaseParam(APP_NAME, "te", BUNDLE_NAME);

        // invalid name with cyrillic characters
        BundlersTest.testInvalidValueForBaseParam(APP_NAME, "\u0442\u0435\u0441\u0442", BUNDLE_NAME);
        // invalid name that starts with digit
        BundlersTest.testInvalidValueForBaseParam(APP_NAME, "1test", BUNDLE_NAME);
        // invalid name that one character long
        BundlersTest.testInvalidValueForBaseParam(APP_NAME, "t", BUNDLE_NAME);
    }

    /**
     * See if smoke comes out
     */
    @Test
    public void smokeTest() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        assertNotNull(bundler.getName());
        assertNotNull(bundler.getID());
        assertNotNull(bundler.getDescription());
        //assertNotNull(bundler.getBundleParameters());

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);

        bundleParams.put(APP_NAME.getID(), "Smoke Test");
        bundleParams.put(MAIN_CLASS.getID(), "hello.HelloRectangle");
        bundleParams.put(PREFERENCES_ID.getID(), "the/really/long/preferences/id");
        bundleParams.put(MAIN_JAR.getID(), new RelativeFileSet(fakeMainJar.getParentFile(),
                new HashSet<>(Arrays.asList(fakeMainJar))));
        bundleParams.put(CLASSPATH.getID(), "mainApp.jar");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(LICENSE_FILE.getID(), Arrays.asList("LICENSE", "LICENSE2"));
        bundleParams.put(LICENSE_TYPE.getID(), "GPL2 + Classpath Exception");
        bundleParams.put(VERBOSE.getID(), true);
        // bundleParams.put(ICON.getID(), "java-logo2.gif");

        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        File result = bundler.execute(bundleParams, new File(workDir, "smoke"));
        System.out.println("Bundle at - " + result);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    /**
     * The bare minimum configuration needed to make it work
     * <ul>
     *     <li>Where to build it</li>
     *     <li>The jar containing the application (with a main-class attribute)</li>
     * </ul>
     *
     * All other values will be driven off of those two values.
     */
    @Test
    public void minimumConfig() {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));

        File output = bundler.execute(bundleParams, new File(workDir, "BareMinimum"));
        System.err.println("Bundle at - " + output);
        assertNotNull(output);
        assertTrue(output.exists());
    }

    /**
     * Test with unicode in places we expect it to be
     */
    @Test
    public void unicodeConfig() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(APP_NAME.getID(), "хелловорлд");
        bundleParams.put(TITLE.getID(), "ХеллоВорлд аппликейшн");
        bundleParams.put(VENDOR.getID(), "Оракл девелопмент");
        bundleParams.put(DESCRIPTION.getID(), "крайне большое описание со странными символами");
        bundleParams.put(EMAIL.getID(), "вася@пупкин.ком");

        // mandatory re-names
        bundleParams.put(BUNDLE_NAME.getID(), "helloworld");

        bundler.validate(bundleParams);

        File output = bundler.execute(bundleParams, new File(workDir, "Unicode"));
        System.err.println("Bundle at - " + output);
        assertNotNull(output);
        assertTrue(output.exists());
    }

    /**
     * prove we fail when bundlename inherited from appname is bad
     */
    @Test(expected = ConfigException.class)
    public void badUnicodeAppName() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(APP_NAME.getID(), "хелловорлд");

        bundler.validate(bundleParams);
    }

    @Test(expected = ConfigException.class)
    public void invalidLicenseFile() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(LICENSE_FILE.getID(), "BOGUS_LICENSE");

        bundler.validate(bundleParams);
    }

    @Test
    public void configureEverything() throws Exception {
        Bundler bundler = new LinuxDebBundler();
        Collection<BundlerParamInfo<?>> parameters = bundler.getBundleParameters();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(APP_NAME.getID(), "Everything App Name");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(ARGUMENTS.getID(), Arrays.asList("He Said", "She Said"));
        bundleParams.put(CLASSPATH.getID(), "mainApp.jar");
        bundleParams.put(LINUX_RUNTIME.getID(), System.getProperty("java.home"));
        bundleParams.put(JVM_OPTIONS.getID(), "-Xms128M");
        bundleParams.put(JVM_PROPERTIES.getID(), "everything.jvm.property=everything.jvm.property.value");
        bundleParams.put(MAIN_CLASS.getID(), "hello.HelloRectangle");
        bundleParams.put(MAIN_JAR.getID(), "mainApp.jar");
        bundleParams.put(MODULE.getID(), "com.everything");
        bundleParams.put(PREFERENCES_ID.getID(), "everything/preferences/id");
        bundleParams.put(PRELOADER_CLASS.getID(), "hello.HelloPreloader");
        bundleParams.put(USER_JVM_OPTIONS.getID(), "-Xmx=256M\n");
        bundleParams.put(VERSION.getID(), "1.2.3.4");
        bundleParams.put(BUNDLE_NAME.getID(), "everything-bundle-name");
        bundleParams.put(COPYRIGHT.getID(), "(C) 2014 - Everything Copyright");
        bundleParams.put(CATEGORY.getID(), "everything category");
        bundleParams.put(DESCRIPTION.getID(), "This is a description of everything");
        bundleParams.put(EMAIL.getID(), "everything@example.com");
        bundleParams.put(ICON_PNG.getID(), "javalogo_white_48.png");
        bundleParams.put(LICENSE_FILE.getID(), "LICENSE");
        bundleParams.put(LICENSE_TYPE.getID(), "GPL v2 + CLASSPATH");
        bundleParams.put(TITLE.getID(), "Everything Title");
        bundleParams.put(VENDOR.getID(), "Everything Vendor");

        // assert they are set
        for (BundlerParamInfo bi : parameters) {
            assertTrue("Bundle args should contain " + bi.getID(), bundleParams.containsKey(bi.getID()));
        }

        // and only those are set
        bundleParamLoop:
        for (String s : bundleParams.keySet()) {
            for (BundlerParamInfo<?> bpi : parameters) {
                if (s.equals(bpi.getID())) {
                    continue bundleParamLoop;
                }
            }
            fail("Enumerated parameters does not contain " + s);
        }

        // assert they resolve
        for (BundlerParamInfo bi : parameters) {
            bi.fetchFrom(bundleParams);
        }

        // add verbose now that we are done scoping out parameters
        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(VERBOSE.getID(), true);

        // assert it validates
        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        // only run the bundle with full tests
        assumeTrue(Boolean.parseBoolean(System.getProperty("FULL_TEST")));

        File result = bundler.execute(bundleParams, new File(workDir, "everything"));
        System.out.println("Bundle at - " + result);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    @Test
    public void servicePackage() throws Exception {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(SERVICE_HINT.getID(), true);
        bundleParams.put(START_ON_INSTALL.getID(), true);
        bundleParams.put(STOP_ON_UNINSTALL.getID(), true);
        bundleParams.put(RUN_AT_STARTUP.getID(), true);
        bundleParams.put(APP_NAME.getID(), "Java Packager Service Test");
        bundleParams.put(BUNDLE_NAME.getID(), "j-p-daemon-test");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(MAIN_CLASS.getID(), "hello.HelloService");
        bundleParams.put(MAIN_JAR.getID(), "mainApp.jar");
        bundleParams.put(CLASSPATH.getID(), "mainApp.jar");
        bundleParams.put(DESCRIPTION.getID(), "Does a random heart beat every 30 seconds or so to a log file in tmp");
        bundleParams.put(LICENSE_FILE.getID(), "LICENSE");
        bundleParams.put(LICENSE_TYPE.getID(), "GPL v2 + CLASSPATH");
        bundleParams.put(VENDOR.getID(), "Packager Tests");
        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(VERBOSE.getID(), true);

        // assert it validates
        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        File result = bundler.execute(bundleParams, new File(workDir, "service"));
        System.out.println("Bundle at - " + result);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    @Test(expected = ConfigException.class)
    public void invalidServiceAppName() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(SERVICE_HINT.getID(), true);
        bundleParams.put(BUNDLE_NAME.getID(), "ThisAppNameIsWayToLongForInitDToHandleGracefully");

        bundler.validate(bundleParams);
    }

    /**
     * multiple launchers
     */
    @Test
    public void twoLaunchersTest() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        assertNotNull(bundler.getName());
        assertNotNull(bundler.getID());
        assertNotNull(bundler.getDescription());
        //assertNotNull(bundler.getBundleParameters());

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_NAME.getID(), "Two Launchers Test");
        bundleParams.put(MAIN_CLASS.getID(), "hello.HelloRectangle");
        bundleParams.put(PREFERENCES_ID.getID(), "the/really/long/preferences/id");
        bundleParams.put(MAIN_JAR.getID(), new RelativeFileSet(fakeMainJar.getParentFile(),
                new HashSet<>(Arrays.asList(fakeMainJar))));
        bundleParams.put(CLASSPATH.getID(), "mainApp.jar");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(VERBOSE.getID(), true);

        List<Map<String, ? super Object>> secondaryLaunchers = new ArrayList<>();
        for (String name : new String[] {"Fire", "More Fire"}) {
            Map<String, ? super Object> launcher = new HashMap<>();
            launcher.put(APP_NAME.getID(), name);
            launcher.put(PREFERENCES_ID.getID(), "secondary/launcher/" + name);
            secondaryLaunchers.add(launcher);
        }
        bundleParams.put(SECONDARY_LAUNCHERS.getID(), secondaryLaunchers);

        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        File output = bundler.execute(bundleParams, new File(workDir, "launchers"));
        assertNotNull(output);
        assertTrue(output.exists());
        assertTrue(output.isFile());
        assertTrue(output.length() > 1_000_000);
    }

    /**
     * Set File Association
     */
    @Test
    public void testFileAssociation() throws ConfigException, UnsupportedPlatformException {
        // only run the bundle with full tests
        assumeTrue(Boolean.parseBoolean(System.getProperty("FULL_TEST")));

        testFileAssociation("FASmoke 1", "Bogus File", "bogus", "application/x-vnd.test-bogus",
                            new File(appResourcesDir, "javalogo_white_48.png"));
    }

    @Test
    public void testFileAssociationWithNullExtension() throws ConfigException, UnsupportedPlatformException {
        // association with no extension is still valid case (see RT-38625)
        testFileAssociation("FASmoke null", "Bogus File", null, "application/x-vnd.test-bogus",
                            new File(appResourcesDir, "javalogo_white_48.png"));
    }

    @Test
    public void testFileAssociationWithMultipleExtension() throws ConfigException, UnsupportedPlatformException {
        // only run the bundle with full tests
        assumeTrue(Boolean.parseBoolean(System.getProperty("FULL_TEST")));

        testFileAssociation("FASmoke ME", "Bogus File", "bogus fake", "application/x-vnd.test-bogus",
                new File(appResourcesDir, "javalogo_white_48.png"));
    }

    @Test
    public void testMultipleFileAssociation()
            throws ConfigException, UnsupportedPlatformException {
        // only run the bundle with full tests
        assumeTrue(Boolean.parseBoolean(System.getProperty("FULL_TEST")));

        testFileAssociationMultiples("FASmoke MA",
                new String[] {"Bogus File", "Fake file"},
                new String[] {"bogus", "fake"},
                new String[] {"application/x-vnd.test-bogus", "application/x-vnd.test-fake"},
                new File[] {new File(appResourcesDir, "javalogo_white_48.png"), new File(appResourcesDir,
                        "javalogo_white_48.png")});
    }

    @Test
    public void testMultipleFileAssociationWithMultipleExtension()
            throws ConfigException, UnsupportedPlatformException {
        // association with no extension is still valid case (see RT-38625)
        testFileAssociationMultiples("FASmoke MAME",
                new String[]{"Bogus File", "Fake file"},
                new String[]{"bogus boguser", "fake faker"},
                new String[]{"application/x-vnd.test-bogus", "application/x-vnd.test-fake"},
                new File[]{new File(appResourcesDir, "javalogo_white_48.png"), new File(appResourcesDir, "javalogo_white_48.png")});
    }

    private void testFileAssociation(String appName, String description, String extensions,
                                     String contentType, File icon)
            throws ConfigException, UnsupportedPlatformException {
        testFileAssociationMultiples(appName, new String[] {description}, new String[] {extensions},
                new String[] {contentType}, new File[] {icon});
    }

    private void testFileAssociationMultiples(String appName, String[] description, String[] extensions,
                                              String[] contentType, File[] icon)
            throws ConfigException, UnsupportedPlatformException {
        assertEquals("Sanity: description same length as extensions", description.length, extensions.length);
        assertEquals("Sanity: extensions same length as contentType", extensions.length, contentType.length);
        assertEquals("Sanity: contentType same length as icon", contentType.length, icon.length);

        AbstractBundler bundler = new LinuxDebBundler();

        assertNotNull(bundler.getName());
        assertNotNull(bundler.getID());
        assertNotNull(bundler.getDescription());
        //assertNotNull(bundler.getBundleParameters());

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_NAME.getID(), appName);
        bundleParams.put(MAIN_CLASS.getID(), "hello.HelloRectangle");
        bundleParams.put(MAIN_JAR.getID(), new RelativeFileSet(fakeMainJar.getParentFile(),
                new HashSet<>(Arrays.asList(fakeMainJar))));
        bundleParams.put(CLASSPATH.getID(), "mainApp.jar");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(VERBOSE.getID(), true);
        bundleParams.put(SYSTEM_WIDE.getID(), true);
        bundleParams.put(VENDOR.getID(), "Packager Tests");

        List<Map<String, Object>> associations = new ArrayList<>();

        for (int i = 0; i < description.length; i++) {
            Map<String, Object> fileAssociation = new HashMap<>();
            fileAssociation.put(FA_DESCRIPTION.getID(), description[i]);
            fileAssociation.put(FA_EXTENSIONS.getID(), extensions[i]);
            fileAssociation.put(FA_CONTENT_TYPE.getID(), contentType[i]);
            fileAssociation.put(FA_ICON.getID(), icon[i]);

            associations.add(fileAssociation);
        }

        bundleParams.put(FILE_ASSOCIATIONS.getID(), associations);

        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        File result = bundler.execute(bundleParams, new File(workDir, APP_FS_NAME.fetchFrom(bundleParams)));
        System.out.println("Bundle at - " + result);
        assertNotNull(result);
        assertTrue(result.exists());
    }

    /*
     * Test that bundler doesn't support per-user daemons (RT-37985)
     */
    @Test(expected = ConfigException.class)
    public void perUserDaemonTest() throws ConfigException, UnsupportedPlatformException {
        AbstractBundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(SERVICE_HINT.getID(), true);
        bundleParams.put(SYSTEM_WIDE.getID(), false);

        bundler.validate(bundleParams);
    }

    @Test
    public void perSystemDaemonTest() throws ConfigException, UnsupportedPlatformException {
        AbstractBundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(SERVICE_HINT.getID(), true);
        bundleParams.put(SYSTEM_WIDE.getID(), true);

        bundler.validate(bundleParams);
    }

    /**
     * Turn on AppCDS
     */
    @Test
    public void testAppCDS() throws ConfigException, UnsupportedPlatformException {
        Bundler bundler = new LinuxDebBundler();

        Map<String, Object> bundleParams = new HashMap<>();

        // not part of the typical setup, for testing
        bundleParams.put(BUILD_ROOT.getID(), tmpBase);
        bundleParams.put(VERBOSE.getID(), true);
        if (runtimeJdk != null) {
            bundleParams.put(LINUX_RUNTIME.getID(), runtimeJdk);
        }

        bundleParams.put(APP_NAME.getID(), "AppCDS");
        bundleParams.put(IDENTIFIER.getID(), "com.example.appcds.deb.Test");
        bundleParams.put(APP_RESOURCES.getID(), new RelativeFileSet(appResourcesDir, appResources));
        bundleParams.put(ENABLE_APP_CDS.getID(), true);

        boolean valid = bundler.validate(bundleParams);
        assertTrue(valid);

        File output = bundler.execute(bundleParams, new File(workDir, "CDSTest"));
        System.err.println("Bundle at - " + output);
        assertNotNull(output);
        assertTrue(output.exists());
    }
}
