package com.sun.openjfx.tools.packager;

import java.io.File;
import java.text.MessageFormat;

public class Help {

    static final String HELP_MSG = "Usage: javapackager -command [-options]\n" +
            "     \n" +
            "where command is one of:\n" +
            "  -createjar\n" +
            "          The packager produces jar archive according to other parameters.\n" +
            "  -deploy\n" +
            "          The packager generates (what?) according to other\n" +
            "          parameters.\n" +
            "  -createbss\n" +
            "          Converts css file into binary form\n" +
            "  -signJar\n" +
            "          Signs jar file(s) with a provided certificate.\n" +
            "  -makeall\n" +
            "          Performs compilation, createjar and deploy steps as one call with\n" +
            "          most arguments predefined. The sources must be located in \"src\"\n" +
            "          folder, the resulting files (jar, html) are put in \"dist\"\n" +
            "          folder. This command may be configured only in a minimal way and is\n" +
            "          as automated as possible.\n"
            +
            "Options for createjar command include:\n" +
            "  -appclass <application class>\n" +
            "         qualified name of the application class to be executed.\n" +
            "  -preloader <preloader class>\n" +
            "         qualified name of the preloader class to be executed.\n" +
            "  -paramfile <file>\n" +
            "         properties file with default named application parameters.\n" +
            "  -classpath <files>\n" +
            "         list of dependent jar file names.\n" +
            "  -manifestAttrs <manifest attributes>\n" +
            "         List of additional manifest attributes. Syntax: \"name1=value1,\n" +
            "         name2=value2,name3=value3.\n" +
            "  -nocss2bin\n" +
            "         The packager won't convert CSS files to binary form before copying\n" +
            "         to jar.\n" +
            "  -runtimeversion <version>\n" +
            "         version of the required JavaFX Runtime.\n" +
            "  -outdir <dir>\n" +
            "         name of the directory to generate output file to.\n" +
            "  -outfile <filename>\n" +
            "         The name (without the extension) of the resulting file.\n" +
            "  -srcdir <dir>\n" +
            "         Base dir of the files to pack.\n" +
            "  -srcfiles <files>\n" +
            "         List of files in srcdir. If omitted, all files in srcdir (which\n" +
            "         is a mandatory argument in this case) will be packed.\n"
            + MessageFormat.format("Options for deploy command include:\n" +
            "  -native <type>\n" +
            "          generate self-contained application bundles (if possible).\n" +
            "          If type is specified then only bundle of this type is created.\n" +
            "          List of supported types includes: installer, image, exe, msi, dmg, pkg, rpm, deb.\n" +
            "  -name <name>\n" +
            "          name of the application.\n" +
            "  -appclass <application class>\n" +
            "          qualified name of the application class to be executed.\n" +
            "  -outdir <dir>\n" +
            "          name of the directory to generate output file to.\n" +
            "  -outfile <filename>\n" +
            "          The name (without the extension) of the resulting file.\n" +
            "  -srcdir <dir>\n" +
            "          Base dir of the files to pack.\n" +
            "  -srcfiles <files>\n" +
            "          List of files in srcdir. If omitted, all files in srcdir (which\n" +
            "          is a mandatory argument in this case) will be used.\n" +
            "  -m <modulename>[/<mainclass>]\n" +
            "  --module <modulename>[/<mainclass>]\n" +
            "          the initial module to resolve, and the name of the main class\n" +
            "          to execute if not specified by the module\n" +
            "  -p <module path>\n" +
            "  --module-path <module path>...\n" +
            "          A {0} separated list of directories, each directory\n" +
            "          is a directory of modules.\n" +
            "  --add-modules <modulename>[,<modulename>...]\n" +
            "          root modules to resolve in addition to the initial module\n" +
            "  --limit-modules <modulename>[,<modulename>...]\n" +
            "          limit the universe of observable modules\n" +
            "  --strip-native-commands <true/false>\n" +
            "          include or exclude the native commands\n" +
            "  -singleton\n" +
            "          prevents multiple instances of the application from launching.\n" +
            "  -title <title>\n" +
            "          title of the application.\n" +
            "  -vendor <vendor>\n" +
            "          vendor of the application.\n" +
            "  -description <description>\n" +
            "          description of the application.\n" +
            "  -preloader <preloader class>\n" +
            "          qualified name of the preloader class to be executed.\n" +
            "  -paramfile <file>\n" +
            "          properties file with default named application parameters.\n" +
            "  -width <width>\n" +
            "          width of the application.\n" +
            "  -height <height>\n" +
            "          height of the application.\n", File.pathSeparator)
            + "Options for createbss command include:\n" +
            "  -outdir <dir>\n" +
            "          name of the directory to generate output file to.\n" +
            "  -srcdir <dir>\n" +
            "          Base dir of the files to pack.\n" +
            "  -srcfiles <files>\n" +
            "          List of files in srcdir. If omitted, all files in srcdir (which\n" +
            "          is a mandatory argument in this case) will be used.\n"
            + "Options for signJar command include:\n" +
            "  -keyStore <file>\n" +
            "          Keystore filename.\n" +
            "  -alias \n" +
            "          Alias for the key.\n" +
            "  -storePass\n" +
            "          Password to check integrity of the keystore or unlock the keystore.\n" +
            "  -keyPass\n" +
            "          Password for recovering the key.\n" +
            "  -storeType\n" +
            "          Keystore type, the default value is \"jks\".\n" +
            "  -outdir <dir>\n" +
            "          name of the directory to generate output file(s) to.\n" +
            "  -srcdir <dir>\n" +
            "          Base dir of the files to signed.\n" +
            "  -srcfiles <files>\n" +
            "          List of files in srcdir. If omitted, all files in srcdir (which\n" +
            "          is a mandatory argument in this case) will be signed.\n"
            + "Options for makeAll command include:\n" +
            "  -appclass <application class>\n" +
            "          qualified name of the application class to be executed.\n" +
            "  -preloader <preloader class>\n" +
            "          qualified name of the preloader class to be executed.\n" +
            "  -classpath <files>\n" +
            "          list of dependent jar file names.\n" +
            "  -name <name>\n" +
            "          name of the application.\n" +
            "  -width <width>\n" +
            "          width of the application.\n" +
            "  -height <height>\n" +
            "          height of the application.\n" +
            "  -v      enable verbose output.\n"
            + "Sample usages:\n" +
            "--------------\n" +
            "javapackager -deploy -native native -outdir outdir -name AppName -m modulename/mainclass\n" +
            "          Generates a native image and all native installers.";
}
