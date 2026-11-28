package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolSource;
import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolInstallerTest {

    @TempDir
    Path directory;

    private Path root;
    private Path source;
    private final Map<String, byte[]> served = new HashMap<>();
    private final ToolHttp http = url -> {
        byte[] body = served.get(url);
        if (body == null) {
            throw new NotFoundException(url);
        }
        return body;
    };
    private final ToolRelease maven = new ToolRelease(Tool.MAVEN, "3.3.9", BuildTools.MAVEN.zipUrl("3.3.9"), null, false);

    @BeforeEach
    void setUp() throws IOException {
        root = directory.resolve("Java");
        source = directory.resolve("download.zip");
    }

    /** A zip with one top folder; {@code launcher} null leaves the launcher out. */
    private void zip(String top, String launcher) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source))) {
            if (launcher != null) {
                out.putNextEntry(new ZipEntry(top + "/" + launcher));
                out.write("@echo off".getBytes(StandardCharsets.US_ASCII));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(top + "/lib/maven-core-3.3.9.jar"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }
        served.put(maven.zipUrl() + ".sha1", ChecksumsSha1.of(source));
    }

    private ToolInstaller installer() {
        ToolInstaller.Downloader downloader = (url, target, progress) -> Files.copy(source, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new ToolInstaller(downloader, new ToolVerifier(http, directory.resolve("keys")), directory.resolve("cache"));
    }

    @Test
    void installsIntoItsOwnVersionFolder() throws Exception {
        zip("apache-maven-3.3.9", "bin/mvn.cmd");

        ToolInstallation installed = installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null);

        Path home = root.resolve("build-tools").resolve("maven-3.3.9");
        assertEquals(home.toString(), installed.path());
        assertEquals(Verification.SHA1, installed.verification());
        assertEquals(ToolSource.DOWNLOADED, installed.source());
        assertTrue(Files.isRegularFile(home.resolve("bin").resolve("mvn.cmd")));
        assertEquals(List.of(home), ToolLayout.installed(root, BuildTools.MAVEN));
        try (var leftovers = Files.list(root.resolve("build-tools"))) {
            assertTrue(leftovers.noneMatch(path -> path.getFileName().toString().startsWith(".unpack-")));
        }
    }

    @Test
    void neverInstallsOverAnExistingVersion() throws Exception {
        zip("apache-maven-3.3.9", "bin/mvn.cmd");
        installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null);

        IOException error = assertThrows(IOException.class, () -> installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null));

        assertTrue(error.getMessage().contains("already installed"));
    }

    @Test
    void anInstalledVersionIsRefusedBeforeAnyDownload() throws Exception {
        zip("apache-maven-3.3.9", "bin/mvn.cmd");
        java.util.concurrent.atomic.AtomicInteger downloads = new java.util.concurrent.atomic.AtomicInteger();
        ToolInstaller counting = new ToolInstaller((url, target, progress) -> {
            downloads.incrementAndGet();
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }, new ToolVerifier(http, directory.resolve("keys")), directory.resolve("cache-a"));
        counting.install(BuildTools.MAVEN, maven, root, Optional.empty(), null);
        ToolInstaller fresh = new ToolInstaller((url, target, progress) -> downloads.incrementAndGet(),
                new ToolVerifier(http, directory.resolve("keys")), directory.resolve("cache-b"));

        assertThrows(IOException.class, () -> fresh.install(BuildTools.MAVEN, maven, root, Optional.empty(), null));

        assertEquals(1, downloads.get());
    }

    @Test
    void anIncompleteFolderInTheWayIsDeletedAndTheInstallContinues() throws Exception {
        Path leftover = root.resolve("build-tools").resolve("maven-3.3.9");
        Files.createDirectories(leftover.resolve("lib"));
        Files.writeString(leftover.resolve("lib").resolve("half-copied.jar"), "x");
        zip("apache-maven-3.3.9", "bin/mvn.cmd");
        List<String> log = new java.util.ArrayList<>();

        ToolInstallation installed = installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), new dev.jdktrustmanager.install.ZipInstaller.InstallProgress() {
            public void log(String line) { log.add(line); }
            public void bytes(long read, long total) { }
            public void phase(String phase) { log.add(phase); }
        });

        assertEquals(leftover.toString(), installed.path());
        assertTrue(Files.isRegularFile(leftover.resolve("bin").resolve("mvn.cmd")));
        assertFalse(Files.exists(leftover.resolve("lib").resolve("half-copied.jar")));
        assertTrue(log.stream().anyMatch(line -> line.contains("Removed an incomplete folder")));
    }

    @Test
    void aFileWhereTheFolderShouldBeIsReplaced() throws Exception {
        Files.createDirectories(root.resolve("build-tools"));
        Files.writeString(root.resolve("build-tools").resolve("maven-3.3.9"), "not a folder");
        zip("apache-maven-3.3.9", "bin/mvn.cmd");

        installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null);

        assertTrue(Files.isRegularFile(root.resolve("build-tools").resolve("maven-3.3.9").resolve("bin").resolve("mvn.cmd")));
    }

    @Test
    void aZipWithoutTheLauncherLeavesNothingBehind() throws Exception {
        zip("apache-maven-3.3.9", null);

        assertThrows(IOException.class, () -> installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null));

        assertFalse(Files.exists(root.resolve("build-tools").resolve("maven-3.3.9")));
    }

    @Test
    void leftoverTempFoldersAreIgnoredAndDoNotBlock() throws Exception {
        Files.createDirectories(root.resolve("build-tools").resolve(".unpack-123").resolve("bin"));
        Files.writeString(root.resolve("build-tools").resolve(".unpack-123").resolve("bin").resolve("mvn.cmd"), "x");
        Files.createDirectories(root.resolve("build-tools").resolve(".adopt-9").resolve("bin"));
        zip("apache-maven-3.3.9", "bin/mvn.cmd");

        installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null);

        assertEquals(List.of(root.resolve("build-tools").resolve("maven-3.3.9")), ToolLayout.installed(root, BuildTools.MAVEN));
    }

    @Test
    void unsafeVersionsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ToolLayout.destination(root, BuildTools.MAVEN, "..\\x"));
        assertThrows(IllegalArgumentException.class, () -> ToolLayout.destination(root, BuildTools.MAVEN, "3.3.9/../../x"));
        assertThrows(IllegalArgumentException.class, () -> ToolLayout.destination(root, BuildTools.MAVEN, "3.3 9"));
        assertEquals(root.resolve("build-tools").resolve("gradle-8.11-rc-1"), ToolLayout.destination(root, BuildTools.GRADLE, "8.11-rc-1"));
    }

    @Test
    void uninstallDeletesOnlyInactiveUnpinnedVersionsInsideBuildTools() throws Exception {
        zip("apache-maven-3.3.9", "bin/mvn.cmd");
        ToolInstallation installed = installer().install(BuildTools.MAVEN, maven, root, Optional.empty(), null);

        assertThrows(IOException.class, () -> installer().uninstall(copy(installed, true, false), root));
        assertThrows(IOException.class, () -> installer().uninstall(copy(installed, false, true), root));
        assertThrows(IOException.class, () -> installer().uninstall(new ToolInstallation(Tool.MAVEN, "21",
                root.resolve("temurin-21").toString(), ToolSource.DOWNLOADED, Verification.SHA1, null, false, false, ""), root));
        installer().uninstall(installed, root);

        assertFalse(Files.exists(Path.of(installed.path())));
    }

    @Test
    void jdkPickerPrefersTheActiveJdkThenTheNewestManagedOneInRange() throws Exception {
        Path jdk8 = fakeJdk("temurin-8", "1.8.0_432");
        Path jdk21 = fakeJdk("temurin-21", "21.0.13");
        List<Installation> managed = List.of(installation(jdk8, 8), installation(jdk21, 21));

        assertEquals(Optional.of(jdk21), JdkPicker.pick(new JavaRange(17, null), jdk8.toString(), managed));
        assertEquals(Optional.of(jdk8), JdkPicker.pick(new JavaRange(7, 8), jdk8.toString(), managed));
        assertEquals(Optional.empty(), JdkPicker.pick(new JavaRange(25, null), jdk21.toString(), managed));
    }

    private Path fakeJdk(String name, String version) throws IOException {
        Path home = root.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
        return home;
    }

    private static Installation installation(Path home, int major) {
        return new Installation("temurin", major, "", "", false, home.toString(), "x64", "", "", "", null,
                InstallSource.FOOJAY, false, null, null, false, "");
    }

    private static ToolInstallation copy(ToolInstallation item, boolean active, boolean pinned) {
        return new ToolInstallation(item.tool(), item.version(), item.path(), item.source(), item.verification(),
                item.installedAt(), active, pinned, item.notes());
    }

    /** SHA-1 checksum file contents for a file. */
    static final class ChecksumsSha1 {
        static byte[] of(Path file) throws IOException {
            return Checksums.hex(file, Verification.SHA1).getBytes(StandardCharsets.US_ASCII);
        }
    }
}
