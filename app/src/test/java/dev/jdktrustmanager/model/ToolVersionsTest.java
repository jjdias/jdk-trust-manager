package dev.jdktrustmanager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolVersionsTest {

    @Test
    void releasesSortAfterTheirOwnPreReleases() {
        assertTrue(ToolVersions.compare("3.0-alpha-2", "3.0-beta-1") < 0);
        assertTrue(ToolVersions.compare("3.0-beta-1", "3.0") < 0);
        assertTrue(ToolVersions.compare("4.0.0-rc-7", "4.0.0") < 0);
        assertTrue(ToolVersions.compare("0.9-rc-1", "0.9") < 0);
        assertTrue(ToolVersions.compare("2.1.0-M1", "2.1.0") < 0);
    }

    @Test
    void numericPartsCompareAsNumbers() {
        assertTrue(ToolVersions.compare("1.10.15", "1.9.16") > 0);
        assertTrue(ToolVersions.compare("3.10.0", "3.9.16") > 0);
        assertEquals(0, ToolVersions.compare("3.9.9", "3.9.9"));
    }

    @Test
    void newestFirstSortsAMixedList() {
        List<String> versions = new ArrayList<>(List.of("3.3.9", "3.10.0", "3.9.16", "4.0.0-rc-7", "2.2.1"));

        versions.sort(ToolVersions.NEWEST_FIRST);

        assertEquals(List.of("4.0.0-rc-7", "3.10.0", "3.9.16", "3.3.9", "2.2.1"), versions);
    }

    @Test
    void preReleasesAreRecognised() {
        assertTrue(ToolVersions.isPreRelease("4.0.0-rc-7"));
        assertTrue(ToolVersions.isPreRelease("2.1.0-M1"));
        assertTrue(ToolVersions.isPreRelease("3.0-alpha-2"));
        assertTrue(ToolVersions.isPreRelease("8.0-milestone-1"));
        assertFalse(ToolVersions.isPreRelease("3.9.16"));
        assertFalse(ToolVersions.isPreRelease("1.10.15"));
    }

    @Test
    void ofSortsNewestFirst() {
        var rows = List.of(ToolInstallationsTest.row(Tool.MAVEN, "3.3.9", false), ToolInstallationsTest.row(Tool.MAVEN, "3.9.9", false));

        assertEquals(List.of("3.9.9", "3.3.9"), ToolInstallations.of(rows, Tool.MAVEN).stream().map(ToolInstallation::version).toList());
    }
}
