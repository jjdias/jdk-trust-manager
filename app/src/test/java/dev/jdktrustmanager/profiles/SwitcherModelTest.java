package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.profiles.SwitcherModel.Item;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SwitcherModelTest {

    private final Profile release = new Profile("Release 4.2", new JdkRef("temurin", 8), "3.3.9", null, null);
    private final Profile nightly = new Profile("Nightly", new JdkRef("temurin", 21), null, null, null);

    @Test
    void labelsAreTheNameOrTheNameWithMissingVersions() {
        List<Item> items = SwitcherModel.items(List.of(release, nightly),
                Map.of("Release 4.2", List.of("temurin 8", "Maven 3.3.9")));

        assertEquals(List.of("Release 4.2", "Nightly"), items.stream().map(Item::name).toList());
        assertEquals("Release 4.2   (missing temurin 8 and Maven 3.3.9)", items.get(0).label());
        assertEquals("Nightly", items.get(1).label());
    }

    @Test
    void titlesNameTheActiveProfileCustomOrUnavailable() {
        assertEquals("Profile: Release 4.2", SwitcherModel.title(Optional.of("Release 4.2"), false));
        assertEquals("Profile: custom", SwitcherModel.title(Optional.empty(), false));
        assertEquals("Profile: unavailable", SwitcherModel.title(Optional.empty(), true));
    }

    @Test
    void selectionIgnoresCase() {
        List<Item> items = SwitcherModel.items(List.of(release, nightly), Map.of());

        assertEquals("Nightly", SwitcherModel.selected(items, Optional.of("NIGHTLY")).orElseThrow().name());
        assertTrue(SwitcherModel.selected(items, Optional.empty()).isEmpty());
        assertTrue(SwitcherModel.selected(items, Optional.of("Other")).isEmpty());
    }

    @Test
    void onlyAnotherProfileIsActivated() {
        List<Item> items = SwitcherModel.items(List.of(release, nightly), Map.of());

        assertFalse(SwitcherModel.shouldActivate(null, Optional.of("Nightly")));
        assertFalse(SwitcherModel.shouldActivate(items.get(1), Optional.of("nightly")));
        assertTrue(SwitcherModel.shouldActivate(items.get(0), Optional.of("Nightly")));
        assertTrue(SwitcherModel.shouldActivate(items.get(0), Optional.empty()));
    }
}
