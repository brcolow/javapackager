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
package com.sun.openjfx.tools.packager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import jdk.tools.jlink.internal.packager.AppRuntimeImageBuilder;

public final class RedistributableModules {
    private static final String JDK_PACKAGER_MODULE = "jdk.packager";

    private RedistributableModules() {}

    private static String stripComments(String line) {
        String result = line.trim();
        int i = result.indexOf(";");

        if (i >= 0) {
            result = result.substring(0, i);
            result = result.trim();
        }

        return result;
    }

    public static Set<String> getRedistributableModules(List<Path> modulePath, String filename) {
        Set<String> result = null;
        Set<String> addModules = new HashSet<>();
        Set<String> limitModules = new HashSet<>();
        ModuleFinder finder = AppRuntimeImageBuilder.moduleFinder(
                modulePath, addModules, limitModules);
        Optional<ModuleReference> mref = finder.find(JDK_PACKAGER_MODULE);

        if (mref.isPresent()) {
            ModuleReader reader = null;

            try {
                reader = mref.get().open();
            } catch (NoSuchElementException | IOException ignore) {
            }

            if (reader != null) {
                Optional<InputStream> stream = null;

                try {
                    stream = reader.open(filename);
                } catch (IOException ignore) {
                }

                if (stream != null) {
                    if (stream.isPresent()) {
                        BufferedReader br;

                        br = new BufferedReader(new InputStreamReader(stream.get(), StandardCharsets.UTF_8));

                        result = new LinkedHashSet<>();
                        String line;

                        try {
                            while ((line = br.readLine()) != null) {
                                String module = stripComments(line);

                                if (!module.isEmpty()) {
                                    result.add(module);
                                }
                            }
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        }

        return result;
    }

    public static String getModuleVersion(File moduleFile, List<Path> modulePath,
                                          Set<String> addModules, Set<String> limitModules) {
        String result = "";

        Module module = new Module(moduleFile);
        ModuleFinder finder = AppRuntimeImageBuilder.moduleFinder(modulePath, addModules, limitModules);
        Optional<ModuleReference> mref = finder.find(module.getModuleName());

        if (mref.isPresent()) {
            ModuleDescriptor descriptor = mref.get().descriptor();

            if (descriptor != null) {
                Optional<ModuleDescriptor.Version> version = descriptor.version();

                if (version.isPresent()) {
                    result = version.get().toString();
                }
            }
        }

        return result;
    }
}
