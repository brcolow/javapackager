/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Module {

    private String filename;
    private ModuleType moduleType;

    public enum JarType { All, UnnamedJar, ModularJar }
    public enum ModuleType { Unknown, UnnamedJar, ModularJar, Jmod, ExplodedModule }

    public Module(File file) {
        super();
        filename = file.getPath();
        moduleType = getModuleType(file);
    }

    public String getModuleName() {
        File file = new File(getFileName());
        // do not try to remove extension for directories
        return moduleType == ModuleType.ExplodedModule ?
                file.getName() : getFileWithoutExtension(file.getName());
    }

    public String getFileName() {
        return filename;
    }

    public ModuleType getModuleType() {
        return moduleType;
    }

    private static ModuleType getModuleType(File file) {
        ModuleType result = ModuleType.Unknown;
        String filename = file.getAbsolutePath();

        if (file.isFile()) {
            if (filename.endsWith(".jmod")) {
                result = ModuleType.Jmod;
            } else if (filename.endsWith(".jar")) {
                JarType status = isModularJar(filename);
                if (status == JarType.ModularJar) {
                    result = ModuleType.ModularJar;
                } else if (status == JarType.UnnamedJar) {
                    result = ModuleType.UnnamedJar;
                }
            }
        } else if (file.isDirectory()) {
            File moduleInfo = new File(filename + File.separator + "module-info.class");

            if (moduleInfo.exists()) {
                result = ModuleType.ExplodedModule;
            }
        }

        return result;
    }

    private static JarType isModularJar(String file) {
        JarType result = JarType.All;

        try {
            ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
            result = JarType.UnnamedJar;

            try {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (entry.getName().matches("module-info.class")) {
                        result = JarType.ModularJar;
                        break;
                    }
                }

                zip.close();
            } catch (IOException ignore) {
            }
        } catch (FileNotFoundException ignore) {
        }

        return result;
    }

    private static String getFileWithoutExtension(String file) {
        return file.replaceFirst("[.][^.]+$", "");
    }
}
