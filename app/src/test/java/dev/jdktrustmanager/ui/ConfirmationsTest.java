package dev.jdktrustmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfirmationsTest {

    @Test
    void removeJdkNamesTheFolderAndSaysItCannotBeUndone() {
        Confirmations.Prompt prompt = Confirmations.removeJdk("C:\\Java\\temurin-17");
        assertEquals("Remove", prompt.action());
        assertTrue(prompt.message().contains("C:\\Java\\temurin-17"));
        assertTrue(prompt.message().contains("can't be undone"));
    }

    @Test
    void setJavaHomeMentionsPathCleanupOnlyWhenChosen() {
        Confirmations.Prompt keep = Confirmations.setJavaHome("C:\\Java\\temurin-21", false);
        Confirmations.Prompt clean = Confirmations.setJavaHome("C:\\Java\\temurin-21", true);
        assertTrue(keep.message().contains("C:\\Java\\temurin-21"));
        assertFalse(keep.message().contains("Other Java entries"));
        assertTrue(clean.message().contains("Other Java entries will be removed"));
    }

    @Test
    void removeUrlSaysImportedCertificatesStay() {
        Confirmations.Prompt prompt = Confirmations.removeUrl("https://example.test");
        assertTrue(prompt.message().contains("https://example.test"));
        assertTrue(prompt.message().contains("stay in each JDK"));
    }

    @Test
    void adoptSaysWhetherItCopiesOrStaysInPlace() {
        Confirmations.Prompt copy = Confirmations.adopt("C:\\Program Files\\Zulu\\zulu-17", "C:\\Java\\zulu-17", false);
        Confirmations.Prompt inPlace = Confirmations.adopt("C:\\Java\\temurin-21", "C:\\Java\\temurin-21", true);
        assertEquals("Copy and adopt", copy.action());
        assertTrue(copy.message().contains("original stays"));
        assertEquals("Adopt", inPlace.action());
        assertTrue(inPlace.message().contains("Nothing is moved"));
    }

    @Test
    void updateNamesBothVersions() {
        Confirmations.Prompt prompt = Confirmations.update("C:\\Java\\temurin-21", "21.0.13", "21.0.14+7");
        assertTrue(prompt.message().contains("21.0.13"));
        assertTrue(prompt.message().contains("21.0.14+7"));
        assertTrue(prompt.message().contains("old version is put back"));
    }

    @Test
    void installToolNamesTheFolderAndHowItIsChecked() {
        Confirmations.Prompt signed = Confirmations.installTool("Maven", "3.3.9", "C:\\Java\\build-tools\\maven-3.3.9", true);
        Confirmations.Prompt gradle = Confirmations.installTool("Gradle", "8.10.2", "C:\\Java\\build-tools\\gradle-8.10.2", false);
        assertEquals("Install", signed.action());
        assertTrue(signed.message().contains("C:\\Java\\build-tools\\maven-3.3.9"));
        assertTrue(signed.message().contains("signing keys"));
        assertTrue(gradle.message().contains("SHA-256"));
    }

    @Test
    void setToolPathIncludesTheJavaWarningWhenGiven() {
        Confirmations.Prompt plain = Confirmations.setToolPath("Maven", "4.0.0", null);
        Confirmations.Prompt warned = Confirmations.setToolPath("Maven", "4.0.0", "Maven 4.0.0 needs Java 17 or newer; JAVA_HOME is Java 8.");
        assertTrue(plain.message().contains("MAVEN") || plain.message().contains("Maven 4.0.0"));
        assertTrue(warned.message().contains("needs Java 17 or newer"));
    }

    @Test
    void removeToolAndRemoveFromPathNameWhatChanges() {
        assertTrue(Confirmations.removeTool("Ant", "1.9.16", "C:\\Java\\build-tools\\ant-1.9.16").message().contains("C:\\Java\\build-tools\\ant-1.9.16"));
        assertTrue(Confirmations.removeToolFromPath("Maven", java.util.List.of("MAVEN_HOME", "M2_HOME")).message().contains("M2_HOME"));
    }

    @Test
    void removeJavaFromPathNamesJavaHomeAndKeepsTools() {
        Confirmations.Prompt prompt = Confirmations.removeJavaFromPath();
        assertEquals("Remove from PATH", prompt.action());
        assertTrue(prompt.message().contains("JAVA_HOME"));
        assertTrue(prompt.message().contains("Maven, Ant, and Gradle"));
    }

    @Test
    void syncAllMentionsBackups() {
        assertTrue(Confirmations.syncAll().message().contains("backup"));
    }
}
