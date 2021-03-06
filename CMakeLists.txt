cmake_minimum_required(VERSION 3.4...3.12)

# Require version 3.4 for handling Windows manifest files gracefully.

if(${CMAKE_VERSION} VERSION_LESS 3.12)
    cmake_policy(VERSION ${CMAKE_MAJOR_VERSION}.${CMAKE_MINOR_VERSION})
endif()

project(javapackager VERSION 0.0.1 LANGUAGES C CXX)
if(WIN32)
    include(InstallRequiredSystemLibraries)
endif()

add_library(launcher_lib SHARED modules/jdk.packager/src/main/native/library/common/Exports.h
            modules/jdk.packager/src/main/native/library/common/Exports.cpp
            modules/jdk.packager/src/main/native/library/common/FilePath.h
            modules/jdk.packager/src/main/native/library/common/FilePath.cpp
            modules/jdk.packager/src/main/native/library/common/GenericPlatform.h
            modules/jdk.packager/src/main/native/library/common/GenericPlatform.cpp
            modules/jdk.packager/src/main/native/library/common/Helpers.h
            modules/jdk.packager/src/main/native/library/common/Helpers.cpp
            modules/jdk.packager/src/main/native/library/common/IniFile.h
            modules/jdk.packager/src/main/native/library/common/IniFile.cpp
            modules/jdk.packager/src/main/native/library/common/Java.h
            modules/jdk.packager/src/main/native/library/common/Java.cpp
            modules/jdk.packager/src/main/native/library/common/JavaUserPreferences.h
            modules/jdk.packager/src/main/native/library/common/JavaUserPreferences.cpp
            modules/jdk.packager/src/main/native/library/common/JavaVirtualMachine.h
            modules/jdk.packager/src/main/native/library/common/JavaVirtualMachine.cpp
            modules/jdk.packager/src/main/native/library/common/Lock.h
            modules/jdk.packager/src/main/native/library/common/Lock.cpp
            modules/jdk.packager/src/main/native/library/common/Macros.h
            modules/jdk.packager/src/main/native/library/common/Macros.cpp
            modules/jdk.packager/src/main/native/library/common/main.cpp
            modules/jdk.packager/src/main/native/library/common/Messages.h
            modules/jdk.packager/src/main/native/library/common/Messages.cpp
            modules/jdk.packager/src/main/native/library/common/OrderedMap.h
            modules/jdk.packager/src/main/native/library/common/Package.h
            modules/jdk.packager/src/main/native/library/common/Package.cpp
            modules/jdk.packager/src/main/native/library/common/Platform.h
            modules/jdk.packager/src/main/native/library/common/Platform.cpp
            modules/jdk.packager/src/main/native/library/common/PlatformString.h
            modules/jdk.packager/src/main/native/library/common/PlatformString.cpp
            modules/jdk.packager/src/main/native/library/common/PlatformThread.h
            modules/jdk.packager/src/main/native/library/common/PlatformThread.cpp
            modules/jdk.packager/src/main/native/library/common/PropertyFile.h
            modules/jdk.packager/src/main/native/library/common/PropertyFile.cpp)

# If this is a CMake debug build, set DEBUG compiler definition (used by native launcher library).
target_compile_definitions(launcher_lib PUBLIC $<$<CONFIG:DEBUG>:DEBUG>)

if(WIN32)
    if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
        message(WARNING "Compiling Windows native binaries without 64-bit!")
    endif()
    add_executable(javapackager modules/jdk.packager/src/main/native/javapackager/win/ByteBuffer.cpp
                   modules/jdk.packager/src/main/native/javapackager/win/ByteBuffer.h
                   modules/jdk.packager/src/main/native/javapackager/win/IconSwap.cpp
                   modules/jdk.packager/src/main/native/javapackager/win/IconSwap.h
                   modules/jdk.packager/src/main/native/javapackager/win/javapackager.cpp
                   modules/jdk.packager/src/main/native/javapackager/win/VersionInfoSwap.cpp
                   modules/jdk.packager/src/main/native/javapackager/win/VersionInfoSwap.h
                   modules/jdk.packager/src/main/native/javapackager/win/javapackager.manifest
                   modules/jdk.packager/src/main/native/javapackager/win/javapackager.rc)
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_COMPANY=Oracle\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_COMPONENT=Java Packager Windows binary\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_NAME=Java Packager Windows 0.0.1\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_VER=0.0.1\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_BUILD_ID=0.0.1\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_COPYRIGHT=Copyright \\u00A9 2018\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_FVER=0.0.0.1\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d \"JFX_FTYPE=0x2L\"")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d JFX_FNAME=javapackager.exe")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /d JFX_INTERNAL_NAME=javapackager")
	set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /l 0x409")
    set_target_properties(javapackager PROPERTIES LINK_FLAGS "/SUBSYSTEM:CONSOLE" "/INCREMENTAL:NO" "/opt:REF")

    add_executable(launcher WIN32 modules/jdk.packager/src/main/native/launcher/win/WinLauncher.cpp)
    set_target_properties(launcher PROPERTIES OUTPUT_NAME "WinLauncher")

    add_executable(launcher_svc WIN32 modules/jdk.packager/src/main/native/service/win/WinLauncherSvc.cpp)
    target_compile_definitions(launcher_svc PRIVATE _WINDOWS UNICODE _UNICODE WIN32 _LITTLE_ENDIAN WIN32_LEAN_AND_MEAN)
    target_include_directories(launcher_svc PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
    target_compile_options(launcher_svc PRIVATE /nologo /W3 /EHsc)
	target_link_libraries(launcher_svc user32 shell32 advapi32 launcher_lib)
    set_target_properties(launcher_svc PROPERTIES OUTPUT_NAME "WinLauncherSvc")

    target_compile_definitions(launcher PRIVATE _WINDOWS UNICODE _UNICODE WIN32 _LITTLE_ENDIAN WIN32_LEAN_AND_MEAN)
    target_include_directories(launcher PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
    target_compile_options(launcher PRIVATE /nologo /W3 /EHsc)
	target_link_libraries(launcher user32 shell32 advapi32 launcher_lib)

    target_sources(launcher_lib PRIVATE modules/jdk.packager/src/main/native/library/common/WindowsPlatform.h)
    target_sources(launcher_lib PRIVATE modules/jdk.packager/src/main/native/library/common/WindowsPlatform.cpp)
    set_target_properties(launcher_lib PROPERTIES OUTPUT_NAME "packager")
    target_compile_definitions(launcher_lib PRIVATE _WINDOWS UNICODE _UNICODE WIN32 _LITTLE_ENDIAN WIN32_LEAN_AND_MEAN)
    target_include_directories(launcher_lib PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
    target_compile_options(launcher_lib PRIVATE /nologo /W3 /EHsc /c)
	target_link_libraries(launcher_lib user32 shell32 advapi32 ole32)

    target_compile_definitions(javapackager PRIVATE UNICODE _UNICODE WIN32 _LITTLE_ENDIAN WIN32_LEAN_AND_MEAN _WIN32_WINDOWS=0X0500 _WIN32_WINNT=0X0500)
    # If this is a CMake debug build, set _DEBUG compiler definition (used by native javapackager on Windows).
    target_compile_definitions(javapackager PUBLIC $<$<CONFIG:DEBUG>:_DEBUG>)
    target_compile_options(javapackager PRIVATE /nologo /W3 /EHsc /MT /GS /O2)
    target_include_directories(javapackager PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)
    target_link_libraries(javapackager kernel32 advapi32)

    add_custom_command(TARGET launcher POST_BUILD COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows)
    add_custom_command(TARGET launcher POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows/$<TARGET_FILE_NAME:launcher>)
    add_custom_command(TARGET launcher_svc POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher_svc>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows/$<TARGET_FILE_NAME:launcher_svc>)
    add_custom_command(TARGET launcher_lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher_lib>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows/$<TARGET_FILE_NAME:launcher_lib>)
    add_custom_command(TARGET javapackager POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:javapackager>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows/$<TARGET_FILE_NAME:javapackager>)

    # Copy msvcp140.dll and vcruntime140.dll into binary output directory and generated-resources.
    foreach(dll ${CMAKE_INSTALL_SYSTEM_RUNTIME_LIBS})
        get_filename_component(dllname ${dll} NAME)
        add_custom_command(TARGET launcher POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy
                ${dll}
                $<TARGET_FILE_DIR:launcher>/${dllname})
        add_custom_command(TARGET launcher POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE_DIR:launcher>/${dllname}
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/windows/${dllname})
    endforeach()
endif()

if(UNIX AND NOT APPLE)
    add_executable(launcher modules/jdk.packager/src/main/native/launcher/linux/launcher.cpp)
    set_target_properties(launcher PROPERTIES OUTPUT_NAME "JavaAppLauncher")
    target_compile_definitions(launcher PRIVATE JAVAARCH=x86_64)
    target_include_directories(launcher PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux)
    target_compile_options(launcher PUBLIC -Wextra -Wformat -Wformat-security)
    target_link_libraries(launcher dl launcher_lib)

    target_sources(launcher_lib PRIVATE modules/jdk.packager/src/main/native/library/common/LinuxPlatform.h
                    modules/jdk.packager/src/main/native/library/common/LinuxPlatform.cpp
                    modules/jdk.packager/src/main/native/library/common/PosixPlatform.h
                    modules/jdk.packager/src/main/native/library/common/PosixPlatform.cpp)
    target_sources(launcher_lib PRIVATE modules/jdk.packager/src/main/native/library/common/LinuxPlatform.cpp)
    set_target_properties(launcher_lib PROPERTIES OUTPUT_NAME "packager")
    target_compile_definitions(launcher_lib PRIVATE JAVAARCH=x86_64)
    target_include_directories(launcher_lib PUBLIC $ENV{JAVA_HOME}/include)
    target_include_directories(launcher_lib PUBLIC $ENV{JAVA_HOME}/include/linux)
    target_compile_options(launcher_lib PUBLIC -Wextra -Wformat -Wformat-security -fPIC)
	target_link_libraries(launcher_lib dl pthread)

    add_custom_command(TARGET launcher POST_BUILD COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/linux)
    add_custom_command(TARGET launcher POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/linux/$<TARGET_FILE_NAME:launcher>)
    add_custom_command(TARGET launcher_lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher_lib>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/linux/$<TARGET_FILE_NAME:launcher_lib>)
endif()

if(APPLE)
    macro(set_xcode_property TARGET XCODE_PROPERTY XCODE_VALUE)
      set_property(TARGET ${TARGET} PROPERTY
        XCODE_ATTRIBUTE_${XCODE_PROPERTY} ${XCODE_VALUE})
    endmacro(set_xcode_property)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "10.9")

    add_executable(launcher modules/jdk.packager/src/main/native/launcher/mac/main.m)
    set_target_properties(launcher PROPERTIES LINKER_LANGUAGE C)
    target_include_directories(launcher PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/darwin)
    set_target_properties(launcher PROPERTIES OUTPUT_NAME "JavaAppLauncher")
    target_compile_definitions(launcher PRIVATE JAVAARCH=x86_64)
    set_property(TARGET launcher PROPERTY C_STANDARD 99)
    target_link_libraries(launcher "-framework Cocoa" launcher_lib)

    target_sources(launcher_lib PRIVATE modules/jdk.packager/src/main/native/library/common/MacPlatform.h
                    modules/jdk.packager/src/main/native/library/common/MacPlatform.mm
                    modules/jdk.packager/src/main/native/library/common/PosixPlatform.h
                    modules/jdk.packager/src/main/native/library/common/PosixPlatform.cpp)
    set_target_properties(launcher_lib PROPERTIES OUTPUT_NAME "packager")
    target_include_directories(launcher_lib PUBLIC $ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/darwin)
    target_link_libraries(launcher_lib dl "-framework Cocoa")
    set_xcode_property(launcher_lib CLANG_CXX_LIBRARY "libc++")

    add_custom_command(TARGET launcher POST_BUILD COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/mac)
    add_custom_command(TARGET launcher POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/mac/$<TARGET_FILE_NAME:launcher>)
    add_custom_command(TARGET launcher_lib POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                $<TARGET_FILE:launcher_lib>
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/build/generated-resources/com/sun/openjfx/tools/packager/mac/$<TARGET_FILE_NAME:launcher_lib>)
endif()

if(NOT WIN32)
    add_custom_command(TARGET launcher POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
                ${CMAKE_SOURCE_DIR}/modules/jdk.packager/src/main/native/javapackager/shell/javapackager
                ${CMAKE_CURRENT_BINARY_DIR}/javapackager)

   # add_custom_command(TARGET launcher POST_BUILD
       # COMMAND ${CMAKE_COMMAND} -E copy
               # ${CMAKE_SOURCE_DIR}/modules/jdk.packager/src/main/main/man1/javapackager.1
               # ${CMAKE_CURRENT_BINARY_DIR}/man/javapackager.1)
endif()
