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

package com.sun.openjfx.tools.packager.bundlers;

public final class Bundler {

    private Bundler() {}

    public enum BundleType {
        NONE,
        ALL,      // Generates all bundlers
        NATIVE,   // Generates both app image and all installers
        IMAGE,    // Generates app image only
        INSTALLER // Generates installers
    }

    public static final class Bundle {
        public BundleType type = BundleType.NONE;
        public String format;
    }

    public static Bundle stringToBundle(String value) {
        Bundle result = new Bundle();

        if (!value.isEmpty()) {
            switch (value) {
                case "false":
                case "none":
                    result.type = BundleType.NONE;
                    break;
                case "all":
                case "true":
                    result.type = BundleType.ALL;
                    break;
                case "image":
                    result.type = BundleType.IMAGE;
                    break;
                case "native":
                    result.type = BundleType.NATIVE;
                    break;
                case "installer":
                    result.type = BundleType.INSTALLER;
                    break;
                default:
                    // assume it is request to build only specific format (like exe or msi)
                    result.type = BundleType.INSTALLER;
                    result.format = value.toLowerCase();
                    break;
            }
        }

        return result;
    }
}
