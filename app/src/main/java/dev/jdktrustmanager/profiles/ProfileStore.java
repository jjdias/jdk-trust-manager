package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads and writes profiles.json. A file it can't read is reported, never overwritten here. */
public final class ProfileStore {

    private final JsonStores stores;
    private final Path file;

    public ProfileStore(JsonStores stores, Path file) {
        this.stores = stores;
        this.file = file;
    }

    public List<Profile> read() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            ProfilesDocument document = stores.readJson(file, ProfilesDocument.class);
            return document.profiles() == null ? List.of() : sorted(document.profiles());
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Couldn't read profiles.json: " + ex.getMessage(), ex);
        }
    }

    /** Validates {@code edited}, replaces the profile named {@code originalNameOrNull} (or adds it), and writes. */
    public List<Profile> save(List<Profile> current, Profile edited, String originalNameOrNull) throws IOException {
        Profile trimmed = trim(edited);
        String problem = validate(current, trimmed, originalNameOrNull);
        if (problem != null) {
            throw new IOException(problem);
        }
        List<Profile> next = new ArrayList<>(current);
        if (originalNameOrNull != null) {
            next.removeIf(profile -> profile.name().equalsIgnoreCase(originalNameOrNull.trim()));
        }
        next.add(trimmed);
        return write(next);
    }

    public List<Profile> delete(List<Profile> current, String name) throws IOException {
        List<Profile> next = new ArrayList<>(current);
        next.removeIf(profile -> profile.name().equalsIgnoreCase(name));
        return write(next);
    }

    /** Null when {@code edited} can be saved, otherwise a message for the user. */
    public static String validate(List<Profile> current, Profile edited, String originalNameOrNull) {
        String name = edited.name() == null ? "" : edited.name().trim();
        if (name.isEmpty()) {
            return "Give the profile a name.";
        }
        if (edited.jdk() == null || edited.jdk().vendorSlug() == null || edited.jdk().vendorSlug().isBlank()) {
            return "Choose a JDK.";
        }
        boolean taken = current.stream()
                .filter(profile -> originalNameOrNull == null || !profile.name().equalsIgnoreCase(originalNameOrNull.trim()))
                .anyMatch(profile -> profile.name().equalsIgnoreCase(name));
        return taken ? "A profile named " + name + " already exists." : null;
    }

    private List<Profile> write(List<Profile> profiles) throws IOException {
        List<Profile> next = sorted(profiles);
        stores.writeJson(file, new ProfilesDocument(next));
        return next;
    }

    private static List<Profile> sorted(List<Profile> profiles) {
        return profiles.stream().sorted(Comparator.comparing(profile -> profile.name().toLowerCase())).toList();
    }

    private static Profile trim(Profile profile) {
        return new Profile(profile.name() == null ? null : profile.name().trim(), profile.jdk(), profile.maven(), profile.ant(), profile.gradle());
    }
}
