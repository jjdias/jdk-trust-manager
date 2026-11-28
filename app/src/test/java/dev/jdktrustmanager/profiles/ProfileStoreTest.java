package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {

    @TempDir
    Path directory;

    private ProfileStore store() {
        return new ProfileStore(new JsonStores(), directory.resolve("profiles.json"));
    }

    static Profile release() {
        return new Profile("Release 4.2", new JdkRef("temurin", 8), "3.3.9", "1.9.16", null);
    }

    static Profile main() {
        return new Profile("Main", new JdkRef("temurin", 21), null, null, "8.10.2");
    }

    @Test
    void roundTripsAndSortsByName() throws Exception {
        List<Profile> saved = store().save(store().save(List.of(), release(), null), main(), null);

        assertEquals(List.of("Main", "Release 4.2"), saved.stream().map(Profile::name).toList());
        assertEquals(saved, store().read());
        assertEquals("3.3.9", release().version(Tool.MAVEN));
        assertNull(release().version(Tool.GRADLE));
    }

    @Test
    void missingFileIsEmpty() throws Exception {
        assertEquals(List.of(), store().read());
    }

    @Test
    void refusesBlankNamesMissingJdksAndDuplicates() {
        List<Profile> current = List.of(release());

        assertEquals("Give the profile a name.", ProfileStore.validate(current, new Profile("  ", new JdkRef("temurin", 8), null, null, null), null));
        assertEquals("Choose a JDK.", ProfileStore.validate(current, new Profile("X", null, null, null, null), null));
        assertEquals("A profile named release 4.2 already exists.",
                ProfileStore.validate(current, new Profile("release 4.2", new JdkRef("temurin", 8), null, null, null), null));
        assertThrows(IOException.class, () -> store().save(current, new Profile("", new JdkRef("temurin", 8), null, null, null), null));
    }

    @Test
    void renamingToTheSameNameInDifferentCaseIsAllowed() throws Exception {
        Profile lower = new Profile("release 4.2", new JdkRef("temurin", 8), "3.3.9", null, null);
        List<Profile> current = store().save(List.of(), lower, null);

        List<Profile> renamed = store().save(current, release(), "release 4.2");

        assertEquals(List.of("Release 4.2"), renamed.stream().map(Profile::name).toList());
    }

    @Test
    void namesAreTrimmed() throws Exception {
        List<Profile> saved = store().save(List.of(), new Profile("  Main  ", new JdkRef("temurin", 21), null, null, null), null);

        assertEquals("Main", saved.get(0).name());
    }

    @Test
    void deleteRemovesOnlyThatProfile() throws Exception {
        List<Profile> both = store().save(store().save(List.of(), release(), null), main(), null);

        assertEquals(List.of("Main"), store().delete(both, "Release 4.2").stream().map(Profile::name).toList());
        assertEquals(List.of("Main"), store().read().stream().map(Profile::name).toList());
    }

    @Test
    void aFileFromANewerVersionIsReportedAndLeftAlone() throws Exception {
        Path file = directory.resolve("profiles.json");
        String newer = "{\"version\":2,\"profiles\":[{\"name\":\"Main\",\"jdk\":{\"vendorSlug\":\"temurin\",\"major\":21},\"terminal\":\"wt\"}]}";
        Files.writeString(file, newer);

        IOException error = assertThrows(IOException.class, () -> store().read());

        assertTrue(error.getMessage().contains("newer version of JDK Trust Manager"));
        assertEquals(newer, Files.readString(file));
    }

    @Test
    void savedFilesCarryTheFormatVersion() throws Exception {
        store().save(List.of(), main(), null);

        assertTrue(Files.readString(directory.resolve("profiles.json")).contains("\"version\" : 1"));
    }

    @Test
    void aMalformedProfileNamesItselfAndLeavesTheFileAlone() throws Exception {
        Path file = directory.resolve("profiles.json");
        String bad = "{\"version\":1,\"profiles\":[{\"name\":\"Broken\",\"jdk\":{\"vendorSlug\":\"..\\\\evil\",\"major\":8}},"
                + "{\"name\":\"NoJdk\"},{\"name\":\"BadMaven\",\"jdk\":{\"vendorSlug\":\"temurin\",\"major\":8},\"maven\":\"3.3 9\"}]}";
        Files.writeString(file, bad);

        IOException error = assertThrows(IOException.class, () -> store().read());

        assertTrue(error.getMessage().startsWith("Couldn't read profiles.json"));
        assertTrue(error.getMessage().contains("Broken"));
        assertEquals(bad, Files.readString(file));
    }

    @Test
    void anUnreadableFileIsReportedAndLeftAlone() throws Exception {
        Path file = directory.resolve("profiles.json");
        Files.writeString(file, "{not json");

        IOException error = assertThrows(IOException.class, () -> store().read());

        assertTrue(error.getMessage().startsWith("Couldn't read profiles.json"));
        assertEquals("{not json", Files.readString(file));
    }
}
