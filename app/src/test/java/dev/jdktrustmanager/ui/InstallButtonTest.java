package dev.jdktrustmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstallButtonTest {

    @Test
    void nothingInstalledSaysInstall() {
        InstallButton.State state = InstallButton.state(null, "21.0.13+9");

        assertEquals("Install", state.label());
        assertTrue(state.enabled());
        assertFalse(state.update());
    }

    @Test
    void anOlderPatchSaysUpdate() {
        InstallButton.State state = InstallButton.state("21.0.12", "21.0.13+9");

        assertEquals("Update", state.label());
        assertTrue(state.enabled());
        assertTrue(state.update());
    }

    @Test
    void theSameVersionSaysInstalledAndIsDisabled() {
        InstallButton.State state = InstallButton.state("21.0.13", "21.0.13+9");

        assertEquals("Installed", state.label());
        assertFalse(state.enabled());
    }

    @Test
    void aBrokenFolderWithoutAVersionStillInstalls() {
        assertEquals("Install", InstallButton.state("", "21.0.13+9").label());
    }
}
