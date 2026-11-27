package dev.jdktrustmanager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolInstallationsTest {

    @Test
    void markActiveTouchesOnlyThatTool() {
        var rows = List.of(row(Tool.MAVEN, "3.9.9", true), row(Tool.MAVEN, "3.3.9", false), row(Tool.ANT, "1.10.15", true));

        var next = ToolInstallations.markActive(rows, Tool.MAVEN, "C:\\Java\\build-tools\\maven-3.3.9");

        assertEquals(List.of(false, true, true), next.stream().map(ToolInstallation::active).toList());
    }

    @Test
    void markActiveNullClearsThatToolOnly() {
        var rows = List.of(row(Tool.MAVEN, "3.9.9", true), row(Tool.ANT, "1.10.15", true));

        var next = ToolInstallations.markActive(rows, Tool.MAVEN, null);

        assertEquals(List.of(false, true), next.stream().map(ToolInstallation::active).toList());
    }

    @Test
    void withPinnedChangesOnlyTheMatchingPath() {
        var rows = List.of(row(Tool.MAVEN, "3.9.9", false), row(Tool.MAVEN, "3.3.9", false));

        var next = ToolInstallations.withPinned(rows, "c:\\java\\build-tools\\MAVEN-3.3.9\\", true);

        assertEquals(List.of(false, true), next.stream().map(ToolInstallation::pinned).toList());
    }

    @Test
    void ofReturnsOnlyThatTool() {
        var rows = List.of(row(Tool.MAVEN, "3.9.9", false), row(Tool.ANT, "1.10.15", false));

        assertEquals(List.of("3.9.9"), ToolInstallations.of(rows, Tool.MAVEN).stream().map(ToolInstallation::version).toList());
    }

    @Test
    void weakIsSha1AndMd5Only() {
        assertTrue(Verification.SHA1.weak());
        assertTrue(Verification.MD5.weak());
        assertFalse(Verification.SHA256.weak());
        assertFalse(Verification.SIGNATURE.weak());
    }

    static ToolInstallation row(Tool tool, String version, boolean active) {
        String prefix = tool.name().toLowerCase();
        return new ToolInstallation(tool, version, "C:\\Java\\build-tools\\" + prefix + "-" + version,
                ToolSource.DOWNLOADED, Verification.SIGNATURE, null, active, false, "");
    }
}
