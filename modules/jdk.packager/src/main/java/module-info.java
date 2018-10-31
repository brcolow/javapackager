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
 * It can be located under the name {@code "javapackager"} using the
 * {@link java.util.spi.ToolProvider}, for example:
 * <pre>{@code
 * ToolProvider javaPackager = ToolProvider.findFirst("javapackager").orElseThrow(...);
 * javaPackager.run(...);
 * }</pre>
 *
 * @moduleGraph
 */
module com.brcolow.javapackager {

    requires java.xml;
    requires java.desktop;
    requires java.logging;
    requires javafx.controls;
    requires javafx.graphics;
    requires jdk.jlink;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;

    exports com.sun.openjfx.tools.packager;
    exports com.sun.openjfx.tools.packager.bundlers;

    uses com.sun.openjfx.tools.packager.Bundler;
    uses com.sun.openjfx.tools.packager.Bundlers;
    provides com.sun.openjfx.tools.packager.Bundlers with
            com.sun.openjfx.tools.packager.BasicBundlers;

    provides com.sun.openjfx.tools.packager.Bundler with
            com.sun.openjfx.tools.packager.linux.LinuxAppBundler,
            com.sun.openjfx.tools.packager.linux.LinuxDebBundler,
            com.sun.openjfx.tools.packager.linux.LinuxRpmBundler,
            com.sun.openjfx.tools.packager.mac.MacAppBundler,
            com.sun.openjfx.tools.packager.mac.MacAppStoreBundler,
            com.sun.openjfx.tools.packager.mac.MacDaemonBundler,
            com.sun.openjfx.tools.packager.mac.MacDmgBundler,
            com.sun.openjfx.tools.packager.mac.MacPkgBundler,
            com.sun.openjfx.tools.packager.windows.WinAppBundler,
            com.sun.openjfx.tools.packager.windows.WinExeBundler,
            com.sun.openjfx.tools.packager.windows.WinMsiBundler,
            com.sun.openjfx.tools.packager.windows.WinServiceBundler;

    provides java.util.spi.ToolProvider
        with com.sun.openjfx.tools.JavaPackagerToolProvider;
}
