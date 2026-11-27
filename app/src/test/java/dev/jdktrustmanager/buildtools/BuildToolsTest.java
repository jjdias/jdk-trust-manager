package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.Verification;
import org.junit.jupiter.api.Test;

class BuildToolsTest {

    @Test
    void mavenUrlsPointAtCentral() {
        assertEquals("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.zip",
                BuildTools.MAVEN.zipUrl("3.3.9"));
        assertEquals(Verification.SHA512, BuildTools.MAVEN.checksumUrls("3.9.9").get(0).kind());
        assertTrue(BuildTools.MAVEN.checksumUrls("3.9.9").get(0).url().startsWith("https://repo.maven.apache.org/"));
        assertEquals("https://downloads.apache.org/maven/KEYS", BuildTools.MAVEN.keysUrl().orElseThrow());
    }

    @Test
    void maven36TakesItsSha512FromTheArchive() {
        Checksum first = BuildTools.MAVEN.checksumUrls("3.6.3").get(0);

        assertEquals(Verification.SHA512, first.kind());
        assertEquals("https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.zip.sha512", first.url());
    }

    @Test
    void antSignatureSitsNextToTheZip() {
        assertTrue(BuildTools.ANT.signatureUrl("1.6.5").orElseThrow().endsWith("/apache-ant-1.6.5-bin.zip.asc"));
        assertEquals("https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.15-bin.zip", BuildTools.ANT.zipUrl("1.10.15"));
    }

    @Test
    void gradleHasNoKeysOrSignature() {
        assertTrue(BuildTools.GRADLE.keysUrl().isEmpty());
        assertTrue(BuildTools.GRADLE.signatureUrl("8.10.2").isEmpty());
        assertTrue(BuildTools.GRADLE.checksumUrls("8.10.2").isEmpty());
    }

    @Test
    void variablesAndPathEntries() {
        assertEquals(java.util.List.of("MAVEN_HOME", "M2_HOME"), BuildTools.MAVEN.homeVariables());
        assertEquals("%MAVEN_HOME%\\bin", BuildTools.MAVEN.pathEntry());
        assertEquals("%ANT_HOME%\\bin", BuildTools.ANT.pathEntry());
        assertEquals("%GRADLE_HOME%\\bin", BuildTools.GRADLE.pathEntry());
        assertEquals(BuildTools.ANT, BuildTools.of(Tool.ANT));
        assertEquals(3, BuildTools.ALL.size());
    }

    @Test
    void javaRanges() {
        assertEquals(8, BuildTools.MAVEN.javaRange("3.9.9").min());
        assertEquals(17, BuildTools.MAVEN.javaRange("4.0.0-rc-7").min());
        assertEquals(7, BuildTools.MAVEN.javaRange("3.3.9").min());
        assertEquals(8, BuildTools.ANT.javaRange("1.10.15").min());
        assertFalse(BuildTools.GRADLE.javaRange("6.9.4").allows(17));
        assertTrue(BuildTools.GRADLE.javaRange("8.10.2").allows(21));
        assertFalse(BuildTools.GRADLE.javaRange("9.0.0").allows(11));
        assertFalse(BuildTools.GRADLE.javaRange("1.12").allows(8));
        assertEquals("Java 8 to 21", new JavaRange(8, 21).describe());
        assertEquals("Java 8 or newer", new JavaRange(8, null).describe());
    }
}
