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

#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <windows.h>

#include "IconSwap.h"
#include "VersionInfoSwap.h"

#define _DEBUG

#ifdef _DEBUG
#include <iostream>
#include <sstream>
#endif

using namespace std;

/*
 * Super simple wrapper application that allows for calling javapackager like an executable on Windows.
 * On *nix we just directly execute the javapackager shell script. This could be replaced by a batch file
 * or even Powershell script but for ease of use it is a "nice-to-a-have" feature.
 */

int wmain(int argc, wchar_t* argv[]) {
    // Just let Windows use the java.exe found in $PATH (greatly simplifies code and this is not an unreasonable
    // expectation for Java developers (this does not affect the packaged results of javapackager, only running
    // javapackager to package an application).
    std::wstring javacmd = L"java.exe";
    std::wstring cmd = L"\"" + javacmd + L"\"";
    std::wstring memory = L"-Xmx512M";
    std::wstring debug = L"";
    std::wstring args = L"";

    for (int i = 1; i < argc; i++) {
        std::wstring argument = argv[i];
        std::wstring debug_arg = L"-J-Xdebug:";
        std::wstring icon_swap_arg = L"--icon-swap";
        std::wstring version_swap_arg = L"--version-swap";

        if (argument.find(L"-J-Xmx", 0) == 0) {
            memory = argument.substr(2, argument.length() - 2);
        } else if (argument.find(debug_arg, 0) == 0) {
            std::wstring address = argument.substr(debug_arg.length(), argument.length() - debug_arg.length());
            debug = L"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + address;
        } else if (argument.find(icon_swap_arg, 0) == 0) {
            if (argc != 4) {
                fwprintf(stderr, TEXT("Usage: javapackager.exe --icon-swap [Icon File Name] [Executable File Name]\n"));
                return 1;
            }

            wprintf(L"Icon File Name: %s\n", argv[i + 1]);
            wprintf(L"Executable File Name: %s\n", argv[i + 2]);

            if (ChangeIcon(argv[i + 1], argv[i + 2]) == true) {
                return 0;
            } else {
                fwprintf(stderr, TEXT("failed\n"));
                return 1;
            }
        } else if (argument.find(version_swap_arg, 0) == 0) {
            if (argc != 4) {
                fwprintf(stderr, TEXT("Usage: javapackager.exe --version-swap [Property File Name] [Executable File Name]\n"));
                return 1;
            }

            fwprintf(stdout, TEXT("Resource File Name: %s\n"), argv[i + 1]);
            fwprintf(stdout, TEXT("Executable File Name: %s\n"), argv[i + 2]);

            VersionInfoSwap vs(argv[i + 1], argv[i + 2]);

            if (vs.PatchExecutable()) {
                return 0;
            } else {
                fwprintf(stderr, TEXT("failed\n"));
                return 1;
            }
        } else {
            args = args + L" \"" + argv[i] + L"\"";
        }
    }

    cmd += debug +
        L" " + memory +
        L" --add-exports=jdk.jlink/jdk.tools.jlink.internal.packager=com.brcolow.javapackager"
        L" --module-path \"build/libs/fxpackager.jar;build/deps/javafx-base-11-win.jar;build/deps/javafx-graphics-11-win.jar;build/deps/javafx-controls-11-win.jar;build/deps/bcprov-jdk15on-1.60.jar;build/deps/bcpkix-jdk15on-1.60.jar\""
        L" --module com.brcolow.javapackager/com.sun.openjfx.tools.packager.Main" +
        L" " + args;

#ifdef _DEBUG
    wcout << "cmd: " << cmd << endl;
#endif

    // Call java.exe with the arguments necessary to run javapackager to package an application.
    STARTUPINFO start;
    PROCESS_INFORMATION pi;
    memset(&start, 0, sizeof(start));
    start.cb = sizeof(start);

    if (!CreateProcess(NULL, (wchar_t *) cmd.data(),
            NULL, NULL, TRUE, NORMAL_PRIORITY_CLASS, NULL, NULL, &start, &pi)) {
        fprintf(stderr, "Cannot start java.exe");
        return EXIT_FAILURE;
    }

    WaitForSingleObject(pi.hProcess, INFINITE);
    unsigned long exitCode;
    GetExitCodeProcess(pi.hProcess, &exitCode);

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return exitCode;
}
