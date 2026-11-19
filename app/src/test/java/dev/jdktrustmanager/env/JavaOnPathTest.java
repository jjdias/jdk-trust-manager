package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaOnPathTest {

    @TempDir
    Path directory;

    @Test
    void firstJavaOnTheEffectivePathWins() throws Exception {
        Path systemJdk = jdk("temurin-8", "1.8.0_422");
        Path userJdk = jdk("temurin-21", "21.0.13");
        Path found = JavaOnPath.findJava(
                systemJdk.resolve("bin").toString() + ";C:\\Windows\\system32",
                "%JAVA_HOME%\\bin",
                Map.of("JAVA_HOME", userJdk.toString()));
        assertEquals(systemJdk.resolve("bin").resolve("java.exe"), found.toAbsolutePath().normalize());
        assertEquals(8, JavaOnPath.jdk(found).major());

        Path onlyUser = JavaOnPath.findJava(
                "C:\\Windows\\system32",
                "%JAVA_HOME%\\bin;C:\\Windows",
                Map.of("JAVA_HOME", userJdk.toString()));
        assertEquals(21, JavaOnPath.jdk(onlyUser).major());
        assertTrue(JavaOnPath.expand("%JAVA_HOME%\\bin", Map.of("JAVA_HOME", "C:\\Java\\temurin-11")).startsWith("C:\\Java\\temurin-11"));
        Files.delete(userJdk.resolve("lib").resolve("security").resolve("cacerts"));
        assertThrows(java.io.IOException.class, () -> JavaOnPath.jdk(onlyUser));
    }

    private Path jdk(String name, String version) throws Exception {
        Path home = directory.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        Files.createDirectories(home.resolve("lib").resolve("security"));
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
        Files.write(home.resolve("lib").resolve("security").resolve("cacerts"), new byte[] {2});
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        return home;
    }
}
