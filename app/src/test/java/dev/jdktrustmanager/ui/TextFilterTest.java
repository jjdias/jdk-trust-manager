package dev.jdktrustmanager.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextFilterTest {

    @Test
    void matchesTextInsideTheValue() {
        assertTrue(TextFilter.matches("Eclipse Temurin 11", "11"));
        assertTrue(TextFilter.matches("Eclipse Temurin 11", "temurin"));
        assertFalse(TextFilter.matches("Eclipse Temurin 21", "11"));
        assertTrue(TextFilter.matches("https://testng.org", "testng"));
        assertTrue(TextFilter.matches("anything", "  "));
    }
}
