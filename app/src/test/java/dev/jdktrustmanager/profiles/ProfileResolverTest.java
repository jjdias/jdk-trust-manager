package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileResolverTest {

    @TempDir
    Path root;

    static void fakeJdk(Path home) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"1.8.0_432\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
    }

    static void fakeTool(Path home, String launcher) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve(launcher), "@echo off");
    }

    private final Profile release = new Profile("Release 4.2", new JdkRef("temurin", 8), "3.3.9", "1.9.16", null);

    @Test
    void foldersFollowTheLayout() {
        ProfileResolver.Resolved resolved = ProfileResolver.resolve(release, root);

        assertEquals(root.resolve("temurin-8"), resolved.jdk().folder());
        assertEquals(root.resolve("build-tools").resolve("maven-3.3.9"), resolved.tools().get(Tool.MAVEN).folder());
        assertEquals(root.resolve("build-tools").resolve("ant-1.9.16"), resolved.tools().get(Tool.ANT).folder());
        assertEquals(List.of(Tool.MAVEN, Tool.ANT), List.copyOf(resolved.tools().keySet()));
    }

    @Test
    void aJdkHomeOnDiskCountsAsInstalledEvenWithoutARecord() throws Exception {
        fakeJdk(root.resolve("temurin-8"));
        fakeTool(root.resolve("build-tools").resolve("maven-3.3.9"), "bin/mvn.cmd");
        Files.createDirectories(root.resolve("build-tools").resolve("ant-1.9.16"));

        ProfileResolver.Resolved resolved = ProfileResolver.resolve(release, root);

        assertTrue(resolved.jdk().installed());
        assertTrue(resolved.tools().get(Tool.MAVEN).installed());
        assertFalse(resolved.tools().get(Tool.ANT).installed());
        assertEquals(List.of("Ant 1.9.16"), resolved.missing().stream().map(ProfileResolver.Slot::label).toList());
    }

    @Test
    void missingListsTheJdkFirstThenToolsInOrder() {
        ProfileResolver.Resolved resolved = ProfileResolver.resolve(release, root);

        assertEquals(List.of("temurin 8", "Maven 3.3.9", "Ant 1.9.16"),
                resolved.missing().stream().map(ProfileResolver.Slot::label).toList());
    }

    @Test
    void describeReadsLikeTheRow() {
        assertEquals("temurin 8 · Maven 3.3.9 · Ant 1.9.16", ProfileResolver.describe(release));
        assertEquals("temurin 21", ProfileResolver.describe(new Profile("Bare", new JdkRef("temurin", 21), null, null, null)));
    }
}
