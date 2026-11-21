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
    void syncAllMentionsBackups() {
        assertTrue(Confirmations.syncAll().message().contains("backup"));
    }
}
