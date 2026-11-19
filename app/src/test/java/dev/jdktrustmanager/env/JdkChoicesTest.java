package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JdkChoicesTest {

    @Test
    void currentJavaHomeComesFirstAndDuplicatesAreDropped() {
        List<String> choices = JdkChoices.candidates(
                "C:\\Java\\temurin-11",
                List.of("C:\\Java\\temurin-21", "c:\\java\\temurin-11\\", "C:\\Java\\zulu-27"));

        assertEquals(List.of("C:\\Java\\temurin-11", "C:\\Java\\temurin-21", "C:\\Java\\zulu-27"), choices);
    }

    @Test
    void blankJavaHomeIsNotAChoice() {
        assertEquals(List.of("C:\\Java\\temurin-21"), JdkChoices.candidates("  ", List.of("C:\\Java\\temurin-21")));
    }
}
