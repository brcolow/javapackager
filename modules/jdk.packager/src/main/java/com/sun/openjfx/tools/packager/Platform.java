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

import java.util.regex.Pattern;

/**
 * Use <code>Platform</code> to detect the operating system that is currently running.
 *
 * Example:
 *
 *  Platform platform = Platform.getPlatform();
 *
 *  switch(PLATFORM) {
 *    case Platform.MAC: {
 *      //TODO Do something
 *      break;
 *    }
 *    case Platform.WINDOWS:
 *    case Platform.LINUX: {
 *      //TODO Do something else
 *    }
 *  }
 */
public enum Platform {

    UNKNOWN,
    WINDOWS,
    LINUX,
    MAC;

    private static final Platform PLATFORM;
    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;

    static {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            PLATFORM = Platform.WINDOWS;
        } else if (os.contains("nix") || os.contains("nux")) {
            PLATFORM = Platform.LINUX;
        } else if (os.contains("mac")) {
            PLATFORM = Platform.MAC;
        } else {
            PLATFORM = Platform.UNKNOWN;
        }

        String version = System.getProperty("os.version");
        String[] parts = version.split(Pattern.quote("."));

        if (parts.length > 0) {
            MAJOR_VERSION = Integer.parseInt(parts[0]);

            if (parts.length > 1) {
                MINOR_VERSION = Integer.parseInt(parts[1]);
            } else {
                MINOR_VERSION = -1;
            }
        } else {
            MAJOR_VERSION = -1;
            MINOR_VERSION = -1;
        }
    }

    Platform() {}

    public static Platform getPlatform() {
        return PLATFORM;
    }

    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public static int getMinorVersion() {
        return MINOR_VERSION;
    }
}
