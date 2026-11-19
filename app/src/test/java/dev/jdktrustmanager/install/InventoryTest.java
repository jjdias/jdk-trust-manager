package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventoryTest {

    @TempDir
    Path directory;

    @Test
    void uninstallKeepsTheZipAndAdoptDoesNotDownload() throws Exception {
        Path root = directory.resolve("Java");
        Path home = root.resolve("temurin-21");
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.13\"\nIMPLEMENTOR=\"Eclipse Adoptium\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
        Path trusted = root.resolve("trusted-certs");
        Files.createDirectories(trusted.resolve("local"));
        Files.writeString(trusted.resolve("release"), "not a jdk");
        Path zip = directory.resolve("cache").resolve("abc.zip");
        Files.createDirectories(zip.getParent());
        Files.write(zip, new byte[] {2});
        Installation installed = new Installation(
                "temurin", 21, "id", "21.0.13", false, home.toString(), "x64", "Eclipse Adoptium", "21.0.13",
                "abc", Instant.now(), InstallSource.FOOJAY, true, null, null, false, "");
        AtomicBoolean cleared = new AtomicBoolean();
        Uninstaller.Result result = new Uninstaller().uninstall(installed, root, null, directory.resolve("cache"), new Uninstaller.ActiveJava() {
            @Override
            public String javaHome() {
                return home.toString();
            }

            @Override
            public void clear() {
                cleared.set(true);
            }
        });
        assertFalse(Files.exists(home));
        assertTrue(Files.isRegularFile(zip));
        assertTrue(Files.isDirectory(trusted));
        assertTrue(cleared.get());
        assertTrue(result.clearedActiveHome());

        Path copied = directory.resolve("copied-temurin");
        Files.createDirectories(copied.resolve("bin"));
        Files.writeString(copied.resolve("release"), "JAVA_VERSION=\"21.0.13\"\nIMPLEMENTOR=\"Eclipse Adoptium\"\n");
        Files.write(copied.resolve("bin").resolve("java.exe"), new byte[] {1});
        Installation adopted = new Adopter().adopt(copied, root, "temurin", 21, false);
        assertEquals(InstallSource.ADOPTED, adopted.source());
        assertEquals("", adopted.zipSha256());
        assertTrue(JdkScanner.isHome(Path.of(adopted.path())));
        assertTrue(Files.exists(copied));

        List<JdkScanner.Discovered> found = new JdkScanner().scan(root, List.of(), List.of(), List.of(
                "    JavaHome    REG_SZ    " + adopted.path()));
        assertTrue(found.stream().anyMatch(row -> row.path().equals(Path.of(adopted.path())) && !row.managed()));
        assertTrue(found.stream().noneMatch(row -> row.path().endsWith("trusted-certs")));
    }

    @Test
    void realTemurinCanBeAdoptedWhenItIsNotRegistered() throws Exception {
        Path temurin = Path.of("C:\\Java\\temurin-21");
        if (!JdkScanner.isHome(temurin)) {
            return;
        }
        List<JdkScanner.Discovered> found = new JdkScanner().scan(Path.of("C:\\Java"), List.of(), List.of(), List.of());
        assertTrue(found.stream().anyMatch(row -> row.path().equals(temurin) && !row.managed()));
    }
}
