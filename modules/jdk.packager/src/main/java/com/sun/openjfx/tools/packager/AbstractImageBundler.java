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

import java.text.MessageFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_JAR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MODULE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.USER_JVM_OPTIONS;

/**
 * Common utility methods used by app image bundlers.
 */
public abstract class AbstractImageBundler extends AbstractBundler {

    public void imageBundleValidation(Map<String, ? super Object> p) throws ConfigException {
        StandardBundlerParam.validateMainClassInfoFromAppResources(p);

        Map<String, String> userJvmOptions = USER_JVM_OPTIONS.fetchFrom(p);
        if (userJvmOptions != null) {
            for (Map.Entry<String, String> entry : userJvmOptions.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    throw new ConfigException(
                            MessageFormat.format("UserJvmOption key ''{0}'' has a null or empty value.",
                                    entry.getKey()), "Provide a value for the key or split the key into a " +
                            "key/value pair.  Such as '-Xmx1G' into '-Xmx' and '1G'.");
                }
            }
        }

        boolean hasMainJar = MAIN_JAR.fetchFrom(p) != null;
        boolean hasMainModule = MODULE.fetchFrom(p) != null;
        boolean hasMainClass = MAIN_CLASS.fetchFrom(p) != null;

        if (!hasMainJar && !hasMainModule && !hasMainClass) {
            throw new ConfigException("Main application class is missing.",
                    "Please specify main application class.");
        }
    }

    public static void extractFlagsFromVersion(Map<String, ? super Object> params, String versionOutput) {
        Pattern bitArchPattern = Pattern.compile("(\\d*)[- ]?[bB]it");
        Matcher matcher = bitArchPattern.matcher(versionOutput);
        if (matcher.find()) {
            params.put(".runtime.bit-arch", matcher.group(1));
        } else {
            // presume 32 bit on no match
            params.put(".runtime.bit-arch", "32");
        }

        Pattern oldVersionMatcher = Pattern.compile("java version \"((\\d+.(\\d+).\\d+)(_(\\d+)))?(-(.*))?\"");
        matcher = oldVersionMatcher.matcher(versionOutput);
        if (matcher.find()) {
            params.put(".runtime.version", matcher.group(1));
            params.put(".runtime.version.release", matcher.group(2));
            params.put(".runtime.version.major", matcher.group(3));
            params.put(".runtime.version.update", matcher.group(5));
            params.put(".runtime.version.minor", matcher.group(5));
            params.put(".runtime.version.security", matcher.group(5));
            params.put(".runtime.version.patch", "0");
            params.put(".runtime.version.modifiers", matcher.group(7));
        } else {
            Pattern newVersionMatcher = Pattern.compile(
                    "java version \"((\\d+).(\\d+).(\\d+).(\\d+))(-(.*))?(\\+[^\"]*)?\"");
            matcher = newVersionMatcher.matcher(versionOutput);
            if (matcher.find()) {
                params.put(".runtime.version", matcher.group(1));
                params.put(".runtime.version.release", matcher.group(1));
                params.put(".runtime.version.major", matcher.group(2));
                params.put(".runtime.version.update", matcher.group(3));
                params.put(".runtime.version.minor", matcher.group(3));
                params.put(".runtime.version.security", matcher.group(4));
                params.put(".runtime.version.patch", matcher.group(5));
                params.put(".runtime.version.modifiers", matcher.group(7));
            } else {
                params.put(".runtime.version", "");
                params.put(".runtime.version.release", "");
                params.put(".runtime.version.major", "");
                params.put(".runtime.version.update", "");
                params.put(".runtime.version.minor", "");
                params.put(".runtime.version.security", "");
                params.put(".runtime.version.patch", "");
                params.put(".runtime.version.modifiers", "");
            }
        }
    }
}
