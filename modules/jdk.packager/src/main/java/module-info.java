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

/**
 * Defines the Java packager tool, javapackager.
 *
 * <p>The javapackager is a tool for generating bundles for self-contained applications.
 * It can be located under the name {@code "javapackager"} using the {@link ToolProvider}, for example:
 * <pre>{@code
 * ToolProvider javaPackager = ToolProvider.findFirst("javapackager").orElseThrow(...);
 * javaPackager.run(...);
 * }</pre>
 *
 * @moduleGraph
 * @since 9
 */
module jdk.packager {
    requires jdk.jlink;

    requires java.xml;
    requires java.desktop;
    requires java.logging;

    exports com.openjdk.tools.packager;
    exports com.sun.openjfx.tools.packager;
    exports com.sun.openjfx.tools.packager.bundlers;
    exports com.sun.openjfx.tools.resource;

    uses com.openjdk.tools.packager.Bundler;
    uses com.openjdk.tools.packager.Bundlers;

    provides com.openjdk.tools.packager.Bundlers with
        com.openjdk.tools.packager.BasicBundlers;

    provides com.openjdk.tools.packager.Bundler with
        com.openjdk.tools.packager.jnlp.JNLPBundler,
        com.openjdk.tools.packager.linux.LinuxAppBundler,
        com.openjdk.tools.packager.linux.LinuxDebBundler,
        com.openjdk.tools.packager.linux.LinuxRpmBundler,
        com.openjdk.tools.packager.mac.MacAppBundler,
        com.openjdk.tools.packager.mac.MacAppStoreBundler,
        com.openjdk.tools.packager.mac.MacDmgBundler,
        com.openjdk.tools.packager.mac.MacPkgBundler,
        com.openjdk.tools.packager.windows.WinAppBundler,
        com.openjdk.tools.packager.windows.WinExeBundler,
        com.openjdk.tools.packager.windows.WinMsiBundler;

    provides java.util.spi.ToolProvider
        with com.openjdk.JavaPackagerToolProvider;
}
