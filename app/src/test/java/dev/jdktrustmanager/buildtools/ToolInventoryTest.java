package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolSource;
import dev.jdktrustmanager.model.Verification;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolInventoryTest {

    private static final Path ROOT = Path.of("C:\\Java");

    private static Path home(String name) {
        return ROOT.resolve("build-tools").resolve(name);
    }

    private static ToolInstallation row(Tool tool, String version, Path home, boolean pinned) {
        return new ToolInstallation(tool, version, home.toString(), ToolSource.DOWNLOADED, Verification.SIGNATURE, null, false, pinned, "");
    }

    @Test
    void anOrphanedVersionFolderIsListedAsUnrecorded() {
        var recorded = row(Tool.MAVEN, "3.9.9", home("maven-3.9.9"), true);
        var ant = row(Tool.ANT, "1.10.15", home("ant-1.10.15"), false);

        List<ToolInstallation> next = ToolInventory.reconcile(List.of(recorded, ant), BuildTools.MAVEN,
                List.of(home("maven-3.9.9"), home("maven-3.3.9")), null);

        assertEquals(3, next.size());
        assertTrue(next.contains(ant));
        assertTrue(next.contains(recorded));
        ToolInstallation orphan = next.stream().filter(item -> item.version().equals("3.3.9")).findFirst().orElseThrow();
        assertEquals(Verification.UNRECORDED, orphan.verification());
        assertEquals(home("maven-3.3.9").toString(), orphan.path());
    }

    @Test
    void rowsWhoseFolderIsGoneAreDroppedAndOnPathComesFromTheRegistry() {
        var gone = row(Tool.MAVEN, "3.6.3", home("maven-3.6.3"), false);
        var kept = row(Tool.MAVEN, "3.9.9", home("maven-3.9.9"), false);

        List<ToolInstallation> next = ToolInventory.reconcile(List.of(gone, kept), BuildTools.MAVEN,
                List.of(home("maven-3.9.9")), home("maven-3.9.9").toString());

        assertEquals(1, next.size());
        assertTrue(next.get(0).active());
    }

    @Test
    void versionComesFromTheFolderName() {
        assertEquals("8.11-rc-1", ToolLayout.versionOf(BuildTools.GRADLE, home("gradle-8.11-rc-1")));
    }
}
