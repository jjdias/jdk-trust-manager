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

    @Test
    void withPinnedChangesOnlyTheChosenRow() {
        List<Installation> known = List.of(
                installation("C:\\Java\\temurin-11", false),
                installation("C:\\Java\\temurin-21", false));

        List<Installation> next = Installations.withPinned(known, "c:\\java\\temurin-21", true);

        assertEquals(List.of(false, true), next.stream().map(Installation::pinned).toList());
    }

    @Test
    void keepUserFlagsCarriesActiveAndPinnedIntoAReinstall() {
        Installation old = Installations.withPinned(List.of(installation("C:\\Java\\temurin-21", true)), "C:\\Java\\temurin-21", true).get(0);
        Installation fresh = new Installation("temurin", 21, "new-id", "21.0.14", false, "C:\\Java\\temurin-21", "x64",
                "Eclipse Adoptium", "21.0.14", "sha2", null, InstallSource.FOOJAY, false, null, null, false, "");

        Installation kept = Installations.keepUserFlags(old, fresh);

        assertEquals("21.0.14", kept.javaVersion());
        assertEquals(true, kept.active());
        assertEquals(true, kept.pinned());
        assertEquals(fresh, Installations.keepUserFlags(null, fresh));
    }

    @Test
    void newerComparesReleasesAndIgnoresBuildNumbers() {
        assertEquals(true, Installations.newer("21.0.14+7", "21.0.13"));
        assertEquals(false, Installations.newer("21.0.13+11", "21.0.13"));
        assertEquals(false, Installations.newer("21.0.12", "21.0.13"));
        assertEquals(true, Installations.newer("21.0.10", "21.0.9"));
        assertEquals(true, Installations.newer("8.0.442+6", "1.8.0_432"));
        assertEquals(true, Installations.newer("22", "21.0.13"));
    }

    private static Installation installation(String path, boolean active) {
        return new Installation("temurin", 21, "id", "21.0.13", false, path, "x64", "Eclipse Adoptium",
                "21.0.13", "sha", null, InstallSource.FOOJAY, active, null, null, false, "");
    }
}
