package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.model.Profile;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What the top-bar profile switcher shows and when picking an entry activates it. */
public final class SwitcherModel {

    public record Item(String name, String label) {}

    private SwitcherModel() {}

    public static List<Item> items(List<Profile> profiles, Map<String, List<String>> missingByName) {
        return profiles.stream().map(profile -> {
            List<String> missing = missingByName.getOrDefault(profile.name(), List.of());
            String label = missing.isEmpty()
                    ? profile.name()
                    : profile.name() + "   (missing " + ProfileActivation.join(missing) + ")";
            return new Item(profile.name(), label);
        }).toList();
    }

    public static String title(Optional<String> active, boolean unavailable) {
        if (unavailable) {
            return "Profile: unavailable";
        }
        return "Profile: " + active.orElse("custom");
    }

    public static Optional<Item> selected(List<Item> items, Optional<String> active) {
        if (active.isEmpty()) {
            return Optional.empty();
        }
        return items.stream().filter(item -> item.name().equalsIgnoreCase(active.get())).findFirst();
    }

    public static boolean shouldActivate(Item picked, Optional<String> active) {
        return picked != null && !active.map(picked.name()::equalsIgnoreCase).orElse(false);
    }
}
