# javapackager

[![Travis CI](https://img.shields.io/travis/brcolow/javapackager/master.svg?label=travis&style=flat-square)](https://travis-ci.org/brcolow/javapackager)
[![AppVeyor Build Status](https://img.shields.io/appveyor/ci/brcolow/javapackager/master.svg?style=flat-square)](https://ci.appveyor.com/project/brcolow/javapackager/branch/master)

## About

This is a stand-alone version of the javapackager tool. The Java
Packager tool can be used to compile, package, sign, and deploy
Java and JavaFX applications from the command line. It can be used as an
alternative to an Ant task or building the applications in an IDE.

## Docs

* [NAME](#name)
* [SYNOPSIS](#synopsis)
* [COMMANDS](#commands)
* [OPTIONS FOR THE CREATEBSS COMMAND](#options-for-the-createbss-command)
* [OPTIONS FOR THE CREATEJAR COMMAND](#options-for-the-createjar-command)
* [OPTIONS FOR THE DEPLOY COMMAND](#options-for-the-deploy-command)
* [OPTIONS FOR THE SIGNJAR COMMAND](#options-for-the-signjar-command)
* [ARGUMENTS FOR SELF-CONTAINED APPLICATION BUNDLERS](#arguments-for-self-contained-application-bundlers)
* [NOTES](#notes)
* [EXAMPLES](#examples)

NAME
----

javapackager − Performs tasks related to packaging and signing Java and JavaFX applications.

SYNOPSIS
--------

**javapackager** _command_ \[_options_\]

_command_

The task that should be performed.

options

One or more options for the command separated by spaces.

COMMANDS
--------

You can specify one of the following commands. After the command, specify the options for it.

−createbss

Converts CSS files into binary form.

−createjar

Produces a JAR archive according to other parameters.

−deploy

Assembles the application package for redistribution. By default, the deploy task generates the base application
package, but it can also generate a self−contained application package if requested.

−signjar

Signs JAR file(s) with a provided certificate.

OPTIONS FOR THE CREATEBSS COMMAND
---------------------------------

−outdir _dir_

Name of the directory that will receive generated output files.

−srcdir _dir_

Base directory of the files to package.

−srcfiles _files_

List of files in the directory specified by the **−srcdir** option. If omitted, all files in the directory (which is a
mandatory argument in this case) will be used. Files in the list must be separated by spaces.

OPTIONS FOR THE CREATEJAR COMMAND
---------------------------------

−appclass _app−class_

Qualified name of the application class to be executed.

−classpath _files_

List of dependent JAR file names.

−manifestAttrs _manifest−attributes_

List of names and values for additional manifest attributes. Syntax:

**"name1=value1,name2=value2,name3=value3"**

−nocss2bin

The packager will not convert CSS files to binary form before copying to JAR.

−outdir _dir_

Name of the directory that will receive generated output files.

−outfile _filename_

Name (without the extension) of the file that will be generated.

−paramfile _file_

A properties file with default named application parameters.

−preloader _preloader−class_

Qualified name of the JavaFX preloader class to be executed. Use this option only for JavaFX applications. Do not use
for Java applications, including headless applications.

−srcdir _dir_

Base directory of the files to package.

−srcfiles _files_

List of files in the directory specified by the **−srcdir** option. If omitted, all files in the directory (which is a
mandatory argument in this case) will be used. Files in the list must be separated by spaces.

OPTIONS FOR THE DEPLOY COMMAND
------------------------------

−appclass _app−class_

Qualified name of the application class to be executed.

−B_bundler−argument=value_

Provides information to the bundler that is used to package a self−contained application. See Arguments for
Self−Contained Application Bundlers for information on the arguments for each bundler.

−callbacks

Specifies user callback methods in generated HTML. The format is the following:

**"name1:value1,name2:value2,..."**

−description _description_

Description of the application.

−name _name_

Name of the application.

−native _type_

Generate self−contained application bundles (if possible). Use the **−B** option to provide arguments to the bundlers
being used. If _type_ is specified, then only a bundle of this type is created. If no type is specified, **all** is
used.

The following values are valid for _type_:

• **all**: Runs all of the installers for the platform on which it is running, and creates a disk image for the
application. This value is used if _type_ is not specified.

• **installer**: Runs all of the installers for the platform on which it is running.

• **image**: Creates a disk image for the application. On OS X, the image is the **.app** file. On Linux, the image is
the directory that gets installed.

• **dmg**: Generates a DMG file for OS X.

• **pkg**: Generates a **.pkg** package for OS X.

• **mac.appStore**: Generates a package for the Mac App Store.

• **rpm**: Generates an RPM package for Linux.

• **deb**: Generates a Debian package for Linux.

−nosign

If present, the bundle generated for self−contained applications is not signed by the bundler. The default for bundlers
that support signing is to sign the bundle if signing keys are properly configured. This attribute is ignored by
bundlers that do not support signing. At the time of the 8u40 release of the JDK, only OS X bundlers support signing.

−outdir _dir_

Name of the directory that will receive generated output files.

−outfile _filename_

Name (without the extension) of the file that will be generated.

−paramfile _file_

Properties file with default named application parameters.

−preloader _preloader−class_

Qualified name of the JavaFX preloader class to be executed. Use this option only for JavaFX applications. Do not use
for Java applications, including headless applications.

−srcdir _dir_

Base directory of the files to package.

−srcfiles _files_

List of files in the directory specified by the **−srcdir** option. If omitted, all files in the directory (which is a
mandatory argument in this case) will be used. Files in the list must be separated by spaces.

OPTIONS FOR THE SIGNJAR COMMAND
-------------------------------

−alias

Alias for the key.

−keyPass

Password for recovering the key.

−keyStore _file_

Keystore file name.

−outdir _dir_

Name of the directory that will receive generated output files.

−srcdir _dir_

Base directory of the files to be signed.

−srcfiles _files_

List of files in the directory specified by the **−srcdir** option. If omitted, all files in the directory (which is a
mandatory argument in this case) will be used. Files in the list must be separated by spaces.

−storePass

Password to check integrity of the keystore or unlock the keystore

−storeType

Keystore type. The default value is "jks".

ARGUMENTS FOR SELF-CONTAINED APPLICATION BUNDLERS
-------------------------------------------------

The **−B** option for the **−deploy** command is used to specify arguments for the bundler that is used to create
self−contained applications. Each type of bundler has its own set of arguments.

**General Bundler Arguments**
appVersion=_version_

Version of the application package. Some bundlers restrict the format of the version string.

classPath=_path_

Class path relative to the assembled application directory. The path is typically extracted from the JAR file manifest,
and does not need to be set if you are using the other **javapackager** commands.

icon=_path_

Location of the default icon to be used for launchers and other assists. For OS X, the format must be **.icns**. For
Linux, the format must be **.png**.

identifier=_value_

Default value that is used for other platform−specific values such as **mac.CFBundleIdentifier**. Reverse DNS order is
recommended, for example, **com.example.application.my−application**.

jvmOptions=_option_

Option to be passed to the JVM when the application is run. Any option that is valid for the **java** command can be
used. To pass more than one option, use multiple instances of the **−B** option, as shown in the following example:

**−BjvmOptions=−Xmx128m −BjvmOptions=−Xms128m**

jvmProperties=_property_\=_value_

Java System Property to be passed to the VM when the application is run. Any property that is valid for the **−D**
option of the **java** command can be used. Specify both the property name and the value for the property. To pass more
than one property, use multiple instances of the **−B** option, as shown in the following example:

**−BjvmProperties=apiUserName=example −BjvmProperties=apiKey=abcdef1234567890**

mainJar=_filename_

Name of the JAR file that contains the main class for the application. The file name is typically extracted from the
JAR file manifest, and does not need to be set if you are using the other **javapackager** commands.

preferencesID=_node_

Preferences node to examine to check for JVM options that the user can override. The node specified is passed to the
application at run time as the option **−Dapp.preferences.id**. This argument is used with the **userJVMOptions**
argument.

runtime=_path_

Location of the JRE or JDK to include in the package bundle. Provide a file path to the root folder of the JDK or JRE.
To use the system default JRE, do not provide a path, as shown in the following example:

**−Bruntime=**

userJvmOptions=_option_\=_value_

JVM options that users can override. Any option that is valid for the **java** command can be used. Specify both the
option name and the value for the option. To pass more than one option, use multiple instances of the **−B** option, as
shown in the following example:

**−BuserJvmOptions=−Xmx=128m −BuserJvmOptions=−Xms=128m**

**OS X Application Bundler Arguments**
mac.category=_category_

Category for the application. The category must be in the list of categories found on the Apple Developer website.

mac.CFBundleIdentifier=_value_

Value stored in the info plist for **CFBundleIdentifier**. This value must be globally unique and contain only letters,
numbers, dots, and dashes. Reverse DNS order is recommended, for example, **com.example.application.my−application**.

mac.CFBundleName=_name_

Name of the application as it appears on the OS X Menu Bar. A name of less than 16 characters is recommended. The
default is the name attribute.

mac.CFBundleVersion=_value_

Version number for the application, used internally. The value must be at least one integer and no more than three
integers separated by periods (.) for example, 1.3 or 2.0.1. The value can be different than the value for the
**appVersion** argument. If the **appVersion** argument is specified with a valid value and the **mac.CFBundleVersion**
argument is not specified, then the **appVersion** value is used. If neither argument is specified, **100** is used as
the version number.

mac.signing−key−developer−id−app=_key_

Name of the signing key used for Devleloper ID or Gatekeeper signing. If you imported a standard key from the Apple
Developer Website, then that key is used by default. If no key can be identified, then the application is not signed.

mac.bundle−id−signing−prefix=_prefix_

Prefix that is applied to the signed binary when binaries that lack plists or existing signatures are found inside the
bundles.

**OS X DMG (Disk Image) Bundler Arguments**
The OS X DMG installer shows the license file specified by **licenseFile**, if provided, before allowing the disk image
to be mounted.

licenseFile=_path_

Location of the End User License Agreement (EULA) to be presented or recorded by the bundler. The path is relative to
the packaged application resources, for example, **−BlicenseFile=COPYING**.

systemWide=_boolean_

Flag that indicates which drag−to−install target to use. Set to **true** to show the Applications folder. Set to
**false** to show the Desktop folder. The default is **true**.

mac.CFBundleVersion=_value_

Version number for the application, used internally. The value must be at least one integer and no more than three
integers separated by periods (.) for example, 1.3 or 2.0.1. The value can be different than the value for the
**appVersion** argument. If the **appVersion** argument is specified with a valid value and the **mac.CFBundleVersion**
argument is not specified, then the **appVersion** value is used. If neither argument is specified, **100** is used as
the version number.

mac.dmg.simple=_boolean_

Flag that indicates if DMG customization steps that depend on executing AppleScript code are skipped. Set to **true** to
skip the steps. When set to **true**, the disk window does not have a background image, and the icons are not moved into
place. If the **systemWide** argument is also set to **true**, then a symbolic link to the root Applications folder is
added to the DMG file. If the **systemWide** argument is set to **false**, then only the application is added to the DMG
file, no link to the desktop is added.

**OS X PKG Bundler Arguments**
The OS X PKG installer presents a wizard and shows the license file specified by **licenseFile** as one of the pages in
the wizard. The user must accept the terms before installing the application.

licenseFile=_path_

Location of the End User License Agreement (EULA) to be presented or recorded by the bundler. The path is relative to
the packaged application resources, for example, **−BlicenseFile=COPYING**.

mac.signing−key−developer−id−installer=_key_

Name of the signing key used for Developer ID or Gatekeeper signing. If you imported a standard key from the Apple
Developer Website, then that key is used by default. If no key can be identified, then the application is not signed.

mac.CFBundleVersion=_value_

Version number for the application, used internally. The value must be at least one integer and no more than three
integers separated by periods (.) for example, 1.3 or 2.0.1. The value can be different than the value for the
**appVersion** argument. If the **appVersion** argument is specified with a valid value and the **mac.CFBundleVersion**
argument is not specified, then the **appVersion** value is used. If neither argument is specified, **100** is used as
the version number.

**Mac App Store Bundler Arguments**
mac.app−store−entitlements=_path_

Location of the file that contains the entitlements that the application operates under. The file must be in the format
specified by Apple. The path to the file can be specified in absolute terms, or relative to the invocation of
**javapackager**. If no entitlements are specified, then the application operates in a sandbox that is stricter than the
typical applet sandbox, and access to network sockets and all files is prevented.

mac.signing−key−app=_key_

Name of the application signing key for the Mac App Store. If you imported a standard key from the Apple Developer
Website, then that key is used by default. If no key can be identified, then the application is not signed.

mac.signing−key−pkg=_key_

Name of the installer signing key for the Mac App Store. If you imported a standard key from the Apple Developer
Website, then that key is used by default. If no key can be identified, then the application is not signed.

mac.CFBundleVersion=_value_

Version number for the application, used internally. The value must be at least one integer and no more than three
integers separated by periods (.) for example, 1.3 or 2.0.1. The value can be different than the value for the
**appVersion** argument. If the **appVersion** argument is specified with a valid value and the **mac.CFBundleVersion**
argument is not specified, then the **appVersion** value is used. If neither argument is specified, **100** is used as
the version number. If this version is an upgrade for an existing application, the value must be greater than previous
version number.

**Linux Debian Bundler Arguments**
The license file specified by **licenseFile** is not presented to the user in all cases, but the file is included in the
application metadata.

category=_category_

Category for the application. See http://standards.freedesktop.org/menu−spec/latest/apa.html for examples.

copyright=_string_

Copyright string for the application. This argument is used in the Debian metadata.

email=_address_

Email address used in the Debian Maintainer field.

licenseFile=_path_

Location of the End User License Agreement (EULA) to be presented or recorded by the bundler. The path is relative to
the packaged application resources, for example, **−BlicenseFile=COPYING**.

licenseType=_type_

Short name of the license type, such as **−BlicenseType=Proprietary**, or **"−BlicenseType=GPL v2 + Classpath Exception"**.

vendor=_value_

Corporation, organization, or individual providing the application. This argument is used in the Debian Maintainer
field.

**Linux RPM Bundler Arguments**
category=_category_

Category for the application. See http://standards.freedesktop.org/menu−spec/latest/apa.html for examples.

licenseFile=_path_

Location of the End User License Agreement (EULA) to be presented or recorded by the bundler. The path is relative to
the packaged application resources, for example, **−BlicenseFile=COPYING**.

licenseType=_type_

Short name of the license type, such as **−BlicenseType=Proprietary**, or **"−BlicenseType=GPL v2 + Classpath Exception"**.

vendor=_value_

Corporation, organization, or individual providing the application.

NOTES
-----

• A **−v** option can be used with any task command to enable verbose output.

• When the **−srcdir** option is allowed in a command, it can be used more than once. If the **−srcfiles** option is
specified, the files named in the argument will be looked for in the location specified in the preceding **srcdir**
option. If there is no **−srcdir** preceding **−srcfiles**, the directory from which the **javapackager** command is
executed is used.

EXAMPLES
--------

**Example 1:**   Using the −createjar Command

`javapackager −createjar −appclass package.ClassName
−srcdir classes −outdir out −outfile outjar −v`

Packages the contents of the **classes** directory to **outjar.jar**, sets the application class to
**package.ClassName**.

Does all the packaging work including compilation, **createjar**, and **deploy**.

**Example 2:**   Using the −signjar Command

`javapackager −signJar −−outdir dist −keyStore sampleKeystore.jks −storePass \*\*\*\*
−alias duke −keypass \*\*\*\* −srcdir dist`

Signs all of the JAR files in the **dist** directory, attaches a certificate with the specified alias, **keyStore** and
**storePass**, and puts the signed JAR files back into the **dist** directory.

**Example 3:**   Using the −deploy Command with Bundler Arguments

```
javapackager −deploy −native deb −Bcategory=Education −BjvmOptions=−Xmx128m
−BjvmOptions=−Xms128m −outdir packages −outfile BrickBreaker −srcdir dist
−srcfiles BrickBreaker.jar −appclass brickbreaker.Main −name BrickBreaker
−title "BrickBreaker demo"
```

Generates the native Linux Debian package for running the BrickBreaker application as a self− contained application.
