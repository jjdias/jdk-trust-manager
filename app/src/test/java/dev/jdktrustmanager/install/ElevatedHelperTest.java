package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElevatedHelperTest {

    @TempDir
    Path directory;

    @Test
    void createRootReportsTheUsersAcl() throws Exception {
        Path root = directory.resolve("Java");
        String result = ElevatedHelper.handle("{\"command\":\"create-root\",\"path\":\"" + root.toString().replace("\\", "\\\\") + "\"}");
        assertTrue(result.contains("\"ok\":true"));
        assertTrue(result.contains("S-1-5-32-545:(OI)(CI)M"));
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void existingJavaRootStaysWritableWithoutRecreation() throws Exception {
        Path java = Path.of("C:\\Java");
        if (!Files.isDirectory(java)) {
            return;
        }
        Path probe = java.resolve(".jkm-write-probe");
        Files.writeString(probe, "ok");
        assertTrue(Files.isRegularFile(probe));
        Files.delete(probe);
        assertFalse(Files.exists(probe));
    }
}
