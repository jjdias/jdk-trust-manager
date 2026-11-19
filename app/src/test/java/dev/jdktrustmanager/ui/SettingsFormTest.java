package dev.jdktrustmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SettingsFormTest {

    @Test
    void parsesWholeHours() {
        assertEquals(24, SettingsForm.parseHours(" 24 "));
    }

    @Test
    void rejectsTextAndValuesBelowOneWithAReadableMessage() {
        IllegalArgumentException text = assertThrows(IllegalArgumentException.class, () -> SettingsForm.parseHours("a day"));
        assertEquals("Catalog cache hours must be a whole number of 1 or more.", text.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SettingsForm.parseHours("0"));
        assertThrows(IllegalArgumentException.class, () -> SettingsForm.parseHours(""));
    }
}
