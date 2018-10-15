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

import com.sun.openjfx.tools.resource.PackagerResource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CreateJarParams extends CommonParams {
    public final List<PackagerResource> resources = new ArrayList<>();

    public String applicationClass;
    public String fallbackClass;
    public String preloader;
    public String classpath;
    public Map<String, String> manifestAttrs;
    public boolean css2bin = true;
    public String outfile;
    public Boolean allPermissions = false;
    public String codebase;

    public List<String> arguments;
    public List<Param> params;

    public void setArguments(List<String> args) {
        this.arguments = args;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    public void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    public void setPreloader(String preloader) {
        this.preloader = preloader;
    }

    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    public void setCss2bin(boolean css2bin) {
        this.css2bin = css2bin;
    }

    public void setOutfile(String outfile) {
        this.outfile = outfile;
    }

    public void setManifestAttrs(Map<String, String> manifestAttrs) {
        this.manifestAttrs = manifestAttrs;
    }

    @Override
    public void addResource(File baseDir, String path) {
        resources.add(new PackagerResource(baseDir, path));
    }

    @Override
    public void addResource(File baseDir, File file) {
        resources.add(new PackagerResource(baseDir, file));
    }

    @Override
    public String toString() {
        return "CreateJarParams{" + "applicationClass=" + applicationClass
                + " preloader=" + preloader + " classpath=" + classpath
                + " manifestAttrs=" + manifestAttrs
                + " css2bin=" + css2bin
                + " outfile=" + outfile + '}'
                + "            CommonParams{" + "outdir=" + outdir
                + " verbose=" + verbose + " resources=" + resources + '}';
    }

    @Override
    public void validate() throws PackagerException {
        if (outfile == null) {
            throw new PackagerException("Error: Missing argument: {0}", "-outfile");
        }
        if (resources.isEmpty()) {
            throw new PackagerException("Error: Missing argument: {0}", "-srcfiles (-srcdir)");
        }
        // otherwise it could be special case of "update jar"
        if (resources.size() != 1 && applicationClass == null) {
            throw new PackagerException("Error: Missing argument: {0}", "-appclass");
        }
    }
}
