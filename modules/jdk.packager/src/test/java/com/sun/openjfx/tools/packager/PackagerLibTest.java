/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackagerLibTest {

    @Rule
    public TemporaryFolder dest = new TemporaryFolder();
    @Rule
    public TemporaryFolder src = new TemporaryFolder();
    static boolean retain = false;
    File srcRoot;
    File destRoot;

    private PackagerLib lib;

    @BeforeClass
    public static void prepareApp() {
        retain = Boolean.parseBoolean(System.getProperty("RETAIN_PACKAGER_TESTS"));
    }

    @Before
    public void setUp() {
        lib = new PackagerLib();
        if (retain) {
            srcRoot = new File("build/tmp/tests/packagerlib/src");
            destRoot = new File("build/tmp/tests/packagerlib/dest");
            srcRoot.mkdirs();
            destRoot.mkdirs();
        } else {
            srcRoot = src.getRoot();
            destRoot = dest.getRoot();
        }
    }

    void validateSignedJar(File jar) throws IOException {
        assertTrue("Expect to be able to read signed jar", jar.canRead());

        ZipInputStream jis = new ZipInputStream(new FileInputStream(jar));

        ZipEntry ze;
        while ((ze = jis.getNextEntry()) != null) {
            if ("META-INF/SIGNATURE.BSF".equalsIgnoreCase(ze.getName())) {
                //found signatures
                return;
            }
        }

        fail("Failed to find signatures in the jar");
    }

    public void doTestSignJar(Manifest m) throws PackagerException, IOException {
        File inputJar = createTestJar(m, "DUMMY.class");

        SignJarParams params = new SignJarParams();
        System.out.println("curr dir: " + Paths.get(".").toAbsolutePath());
        params.setKeyStore(Paths.get("./src/test/resources/com/sun/openjfx/tools", "test.keystore").toFile());
        params.setStorePass("nopassword");
        params.setAlias("simple-http-server");
        params.addResource(inputJar.getParentFile(), inputJar);

        File out = dest.getRoot();

        params.setOutdir(out);

        lib.signJar(params);

        validateSignedJar(new File(out, inputJar.getName()));
    }


    @Test
    public void testSignJar_basic() throws PackagerException, IOException {
        doTestSignJar(new Manifest());
    }

    @Test
    public void testSignJar_noManifest() throws PackagerException, IOException {
        doTestSignJar(null);
    }

    @Test
    public void testSignJar_alreadySigned() throws PackagerException, IOException {
        //TODO: implement creating signed test jar (using normal sign method)
        doTestSignJar(new Manifest());
    }

    private File createTestJar(Manifest m, String entryName) throws IOException {
        File res = File.createTempFile("test", ".jar");
        res.delete();

        if (m != null) {
            //ensure version is there or manifest can be ignored ...
            m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }

        JarOutputStream jos = (m == null) ?
                new JarOutputStream(new FileOutputStream(res)) :
                new JarOutputStream(new FileOutputStream(res), m);

        byte[] content = "Dummy content".getBytes();
        JarEntry entry = new JarEntry(entryName);
        entry.setTime(new Date().getTime());
        jos.putNextEntry(entry);
        jos.write(content, 0, content.length);
        jos.closeEntry();

        jos.close();

        return res;
    }
}
