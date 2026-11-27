package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolSource;
import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolAdopterTest {

    @TempDir
    Path directory;

    private Path home(String name, String launcher, String... libs) throws IOException {
        Path home = directory.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib"));
        Files.writeString(home.resolve(launcher), "@echo off");
        for (String lib : libs) {
            Files.write(home.resolve("lib").resolve(lib), new byte[] {1});
        }
        return home;
    }

    @Test
    void readsEachToolsVersionFromItsHome() throws Exception {
        Path maven = home("apache-maven-3.3.9", "bin/mvn.cmd", "maven-core-3.3.9.jar", "maven-model-3.3.9.jar");
        Path gradle = home("gradle-8.10.2", "bin/gradle.bat", "gradle-launcher-8.10.2.jar");
        Path oldGradle = home("gradle-2.14.1", "bin/gradle.bat", "gradle-core-2.14.1.jar");
        Path ant = home("apache-ant-1.10.15", "bin/ant.bat");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "1.10.15");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(ant.resolve("lib").resolve("ant.jar")), manifest)) {
            jar.flush();
        }

        assertEquals(Optional.of("3.3.9"), ToolAdopter.versionOf(BuildTools.MAVEN, maven));
        assertEquals(Optional.of("8.10.2"), ToolAdopter.versionOf(BuildTools.GRADLE, gradle));
        assertEquals(Optional.of("2.14.1"), ToolAdopter.versionOf(BuildTools.GRADLE, oldGradle));
        assertEquals(Optional.of("1.10.15"), ToolAdopter.versionOf(BuildTools.ANT, ant));
    }

    @Test
    void findsHomesFromVariablesPathAndRootsOnce() throws Exception {
        Path byVariable = home("apache-maven-3.6.3", "bin/mvn.cmd", "maven-core-3.6.3.jar");
        Path byPath = home("tools/maven-3.9.9", "bin/mvn.cmd", "maven-core-3.9.9.jar");
        Path inRoot = home("chocolatey/apache-maven-3.8.8", "bin/mvn.cmd", "maven-core-3.8.8.jar");
        home("Java/build-tools/maven-3.3.9", "bin/mvn.cmd", "maven-core-3.3.9.jar");
        String path = byPath.resolve("bin") + ";%M2_HOME%\\bin;" + directory.resolve("Java/build-tools/maven-3.3.9/bin");

        List<Path> found = ToolAdopter.candidates(BuildTools.MAVEN, Map.of("M2_HOME", byVariable.toString()), path,
                List.of(directory.resolve("chocolatey"), directory.resolve("Java").resolve("build-tools")));

        assertEquals(List.of(byVariable, byPath, inRoot), found);
    }

    @Test
    void adoptCopiesAndKeepsTheOriginal() throws Exception {
        Path source = home("apache-maven-3.3.9", "bin/mvn.cmd", "maven-core-3.3.9.jar");
        Path root = directory.resolve("Java");

        ToolInstallation adopted = new ToolAdopter().adopt(BuildTools.MAVEN, source, root);

        assertEquals(root.resolve("build-tools").resolve("maven-3.3.9").toString(), adopted.path());
        assertEquals(ToolSource.ADOPTED, adopted.source());
        assertEquals(Verification.NONE, adopted.verification());
        assertTrue(Files.isRegularFile(source.resolve("bin").resolve("mvn.cmd")));
        assertTrue(Files.isRegularFile(Path.of(adopted.path()).resolve("bin").resolve("mvn.cmd")));
        assertEquals(List.of(Path.of(adopted.path())), ToolLayout.installed(root, BuildTools.MAVEN));
        IOException again = assertThrows(IOException.class, () -> new ToolAdopter().adopt(BuildTools.MAVEN, source, root));
        assertTrue(again.getMessage().contains("already installed"));
    }
}
