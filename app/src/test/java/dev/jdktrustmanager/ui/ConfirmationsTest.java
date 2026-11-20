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
    void syncAllMentionsBackups() {
        assertTrue(Confirmations.syncAll().message().contains("backup"));
    }
}
