package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Tool;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ParsersTest {

    static String fixture(String name) throws IOException {
        try (InputStream input = ParsersTest.class.getResourceAsStream("/buildtools/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void mavenMetadataListsOldAndNewVersions() throws Exception {
        List<String> versions = MavenMetadataParser.versions(fixture("maven-metadata.xml"));

        assertTrue(versions.contains("2.0.9"));
        assertTrue(versions.contains("3.3.9"));
        assertTrue(versions.contains("4.0.0-rc-7"));
    }

    @Test
    void mavenMetadataRejectsExternalEntities() {
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE m [<!ENTITY x SYSTEM \"file:///C:/Windows/win.ini\">]>"
                + "<metadata><versioning><versions><version>&x;</version></versions></versioning></metadata>";

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> MavenMetadataParser.versions(evil));
    }

    @Test
    void antListingKeepsOnlyBinaryZipsOnce() throws Exception {
        List<String> versions = AntListingParser.versions(fixture("ant-binaries.html"));

        assertTrue(versions.containsAll(List.of("1.5.2", "1.6.5", "1.9.16", "1.10.15")));
        assertEquals(versions.size(), new HashSet<>(versions).size());
        assertFalse(versions.stream().anyMatch(version -> version.endsWith(".asc") || version.contains("src")));
    }

    @Test
    void gradleDropsSnapshotsNightliesAndBrokenAndMarksPreReleases() throws Exception {
        Map<String, ToolRelease> releases = GradleVersionsParser.releases(fixture("gradle-versions.json")).stream()
                .collect(Collectors.toMap(ToolRelease::version, Function.identity()));

        assertFalse(releases.keySet().stream().anyMatch(version -> version.startsWith("9.10.0-") || version.startsWith("10.0.0-")));
        assertFalse(releases.containsKey("1.0-milestone-4"));
        assertTrue(releases.get("9.9.0-rc-1").preRelease());
        assertTrue(releases.get("9.9.0-milestone-2").preRelease());
        assertTrue(releases.get("8.11-rc-1").preRelease());
        ToolRelease stable = releases.get("8.10.2");
        assertFalse(stable.preRelease());
        assertEquals(Tool.GRADLE, stable.tool());
        assertEquals("https://services.gradle.org/distributions/gradle-8.10.2-bin.zip", stable.zipUrl());
        assertTrue(stable.sha256().matches("[0-9a-f]{64}"));
        assertTrue(releases.containsKey("2.14.1"));
    }
}
