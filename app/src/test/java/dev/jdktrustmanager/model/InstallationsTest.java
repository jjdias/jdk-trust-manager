package dev.jdktrustmanager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class InstallationsTest {

    @Test
    void markActiveLeavesOnlyTheChosenPathActive() {
        List<Installation> known = List.of(
                installation("C:\\Java\\temurin-11", false),
                installation("C:\\Java\\temurin-21", true));

        List<Installation> next = Installations.markActive(known, "c:\\java\\TEMURIN-11\\");

        assertEquals(List.of(true, false), next.stream().map(Installation::active).toList());
        assertEquals(known.get(0).path(), next.get(0).path());
    }

    @Test
    void markActiveOnAnUnmanagedPathClearsEveryRow() {
        List<Installation> known = List.of(installation("C:\\Java\\temurin-21", true));

        List<Installation> next = Installations.markActive(known, "C:\\Program Files\\Zulu\\zulu-17");

        assertEquals(List.of(false), next.stream().map(Installation::active).toList());
    }

    private static Installation installation(String path, boolean active) {
        return new Installation("temurin", 21, "id", "21.0.13", false, path, "x64", "Eclipse Adoptium",
                "21.0.13", "sha", null, InstallSource.FOOJAY, active, null, null, false, "");
    }
}
