package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkInUseTest {

    @TempDir
    Path directory;

    @Test
    void programsRunningFromTheJdkAreFoundRegardlessOfCase() {
        Path home = Path.of("C:\\Java\\temurin-21");
        List<JdkInUse.Holder> all = List.of(
                new JdkInUse.Holder(10, "C:\\Java\\temurin-21\\bin\\java.exe"),
                new JdkInUse.Holder(11, "c:\\java\\TEMURIN-21\\bin\\javaw.exe"),
                new JdkInUse.Holder(12, "C:\\Java\\temurin-21-old\\bin\\java.exe"),
                new JdkInUse.Holder(13, "C:\\Windows\\explorer.exe"));

        assertEquals(List.of(10L, 11L), JdkInUse.filter(home, all).stream().map(JdkInUse.Holder::pid).toList());
    }

    @Test
    void theRunningTestJvmIsFoundInItsOwnJdk() {
        List<JdkInUse.Holder> holders = JdkInUse.holders(Path.of(System.getProperty("java.home")));

        assertTrue(holders.stream().anyMatch(holder -> holder.pid() == ProcessHandle.current().pid()));
    }

    @Test
    void anUpdateOfAJdkThatIsRunningIsRefusedBeforeDownloading() throws Exception {
        Path home = directory.resolve("temurin-21");
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.12\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
        var pkg = new dev.jdktrustmanager.model.CatalogPackage("temurin-21", "temurin", 21, "21.0.13+9", "zip", "x64", false, true,
                true, "ga", "lts", "unknown", "OpenJDK21U.zip", 1, false);
        var detail = new dev.jdktrustmanager.catalog.PackageDetail("https://example.invalid/never-fetched.zip", "a".repeat(64), "sha256");
        ZipInstaller installer = new ZipInstaller(folder -> List.of(new JdkInUse.Holder(4242, folder.resolve("bin").resolve("java.exe").toString())));

        JdkInUse.InUseException error = assertThrows(JdkInUse.InUseException.class, () -> installer.install(pkg, detail, directory,
                directory.resolve("cache"), null, ZipInstaller.TrustReplay.noop()));

        assertTrue(error.getMessage().contains("java.exe (pid 4242)"));
        assertTrue(Files.isRegularFile(home.resolve("bin").resolve("java.exe")));
    }

    @Test
    void theMessageNamesEachProgramAndThisApp() {
        String message = JdkInUse.message(Path.of("C:\\Java\\temurin-21"), List.of(
                new JdkInUse.Holder(10, "C:\\Java\\temurin-21\\bin\\java.exe"),
                new JdkInUse.Holder(ProcessHandle.current().pid(), "C:\\Java\\temurin-21\\bin\\java.exe")));

        assertTrue(message.contains("java.exe (pid 10)"));
        assertTrue(message.contains("JDK Trust Manager itself"));
        assertTrue(message.contains("Nothing was downloaded or changed"));
    }

    @Test
    void anOpenFileInTheFolderGivesAPlainMessageAndChangesNothing() throws Exception {
        Path home = directory.resolve("temurin-21");
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.12\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});

        try (FileInputStream open = new FileInputStream(home.resolve("bin").resolve("java.exe").toFile())) {
            IOException error = assertThrows(IOException.class, () -> ZipInstaller.moveAside(home));

            assertTrue(error.getMessage().contains("is in use"), error.getMessage());
            assertTrue(error.getMessage().contains("Nothing was changed"));
        }
        assertTrue(Files.isRegularFile(home.resolve("bin").resolve("java.exe")));
        try (var siblings = Files.list(directory)) {
            assertTrue(siblings.noneMatch(path -> path.getFileName().toString().contains(".bak-")));
        }
    }

    @Test
    void moveAsideReturnsTheBackupWhenNothingHoldsTheFolder() throws Exception {
        Path home = directory.resolve("temurin-17");
        Files.createDirectories(home);

        Path backup = ZipInstaller.moveAside(home);

        assertTrue(Files.isDirectory(backup));
        assertTrue(backup.getFileName().toString().startsWith("temurin-17.bak-"));
    }
}
