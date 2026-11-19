package dev.jdktrustmanager.env;

import java.nio.file.Path;

public final class ShellSnippets {

    private ShellSnippets() {
    }

    public static String powershell(Path home, int major) {
        return "$env:JAVA_HOME = '" + home + "'\n$env:Path = \"$env:JAVA_HOME\\bin;\" + $env:Path\n# major " + major;
    }

    public static String mavenToolchain(Path home, int major) {
        return "<toolchain>\n  <type>jdk</type>\n  <provides><version>" + major + "</version></provides>\n  <configuration><jdkHome>" + home + "</jdkHome></configuration>\n</toolchain>";
    }

    public static String gradleNote(Path home, int major) {
        return "java.toolchain.languageVersion = JavaLanguageVersion.of(" + major + ")\n# " + home;
    }
}
