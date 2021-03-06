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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class ModuleManager {
    private List<String> folders = new ArrayList<>();

    public enum SearchType { UnnamedJar, ModularJar, Jmod, ExplodedModule }

    public ModuleManager(List<Path> paths) {
        super();
        initialize(paths);
    }

    private void initialize(List<Path> paths) {
        for (Path path : paths) {
            folders.add(path.toString().replaceAll("^\"|\"$", ""));
        }
    }

    public List<Module> getModules() {
        return getModules(EnumSet.of(SearchType.UnnamedJar,
                SearchType.ModularJar, SearchType.Jmod, SearchType.ExplodedModule));
    }

    public List<Module> getModules(EnumSet<SearchType> search) {
        List<Module> result = new ArrayList<>();

        for (String folder : folders) {
            result.addAll(getAllModulesInDirectory(folder, search));
        }

        return result;
    }

    private static List<Module> getAllModulesInDirectory(String directory, EnumSet<SearchType> search) {
        List<Module> result = new ArrayList<>();
        File lfolder = new File(directory);
        File[] files = lfolder.listFiles();

        if (files == null) {
            throw new IllegalArgumentException("Can not get modules in directory: " + directory +
                    " because it does not exist");
        }
        for (File file : files) {
            Module module = new Module(file);

            switch (module.getModuleType()) {
                case Unknown:
                    break;
                case UnnamedJar:
                    if (search.contains(SearchType.UnnamedJar)) {
                        result.add(module);
                    }
                    break;
                case ModularJar:
                    if (search.contains(SearchType.ModularJar)) {
                        result.add(module);
                    }
                    break;
                case Jmod:
                    if (search.contains(SearchType.Jmod)) {
                        result.add(module);
                    }
                    break;
                case ExplodedModule:
                    if (search.contains(SearchType.ExplodedModule)) {
                        result.add(module);
                    }
                    break;
            }
        }

        return result;
    }
}
