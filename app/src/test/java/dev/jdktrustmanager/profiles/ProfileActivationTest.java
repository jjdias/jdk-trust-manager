package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileActivationTest {

    @TempDir
    Path root;

    private final Profile release = new Profile("Release 4.2", new JdkRef("temurin", 8), "3.3.9", null, null);

    @Test
    void changesListEverySlotInOrder() {
        assertEquals(List.of("temurin 8 → JAVA_HOME", "Maven 3.3.9 → on PATH", "Ant → off PATH", "Gradle → off PATH"),
                ProfileActivation.changes(ProfileResolver.resolve(release, root)));
    }

    @Test
    void javaWarningsNameTheToolAndBothVersions() {
        Profile maven4 = new Profile("New", new JdkRef("temurin", 8), "4.0.0", null, null);

        assertEquals(List.of("Maven 4.0.0 needs Java 17 or newer; this profile's JDK is Java 8."), ProfileActivation.javaWarnings(maven4));
        assertEquals(List.of(), ProfileActivation.javaWarnings(new Profile("Ok", new JdkRef("temurin", 8), "3.9.9", null, null)));
    }

    @Test
    void readyToWriteOnlyWhenEverySlotIsInstalled() throws Exception {
        assertEquals(Optional.of("Not activated: temurin 8 and Maven 3.3.9 aren't installed."),
                ProfileActivation.readyToWrite(ProfileResolver.resolve(release, root)));
        ProfileResolverTest.fakeJdk(root.resolve("temurin-8"));
        assertEquals(Optional.of("Not activated: Maven 3.3.9 isn't installed."),
                ProfileActivation.readyToWrite(ProfileResolver.resolve(release, root)));
        ProfileResolverTest.fakeTool(root.resolve("build-tools").resolve("maven-3.3.9"), "bin/mvn.cmd");
        assertEquals(Optional.empty(), ProfileActivation.readyToWrite(ProfileResolver.resolve(release, root)));
    }

    @Test
    void unmanagedEntriesThatStillProvideAnOmittedToolAreListed() {
        String path = "%JAVA_HOME%\\bin;C:\\tools\\apache-maven-3.6.3\\bin;%M2_HOME%\\bin;C:\\ProgramData\\chocolatey\\lib\\gradle\\tools\\gradle-7.6\\bin;C:\\Windows";
        Profile withMaven = new Profile("M", new JdkRef("temurin", 8), "3.3.9", null, null);

        assertEquals(List.of(
                        "C:\\ProgramData\\chocolatey\\lib\\gradle\\tools\\gradle-7.6\\bin stays on PATH and still provides Gradle."),
                ProfileActivation.leftoverEntries(ProfileResolver.resolve(withMaven, root), path, root.toString()));
        Profile bare = new Profile("B", new JdkRef("temurin", 8), null, null, null);
        assertEquals(3, ProfileActivation.leftoverEntries(ProfileResolver.resolve(bare, root), path, root.toString()).size());
        assertEquals(List.of(), ProfileActivation.leftoverEntries(ProfileResolver.resolve(bare, root), "C:\\Windows", root.toString()));
    }

    @Test
    void jdkSourceFindsTheCatalogVendorForASlug() {
        List<Distribution> vendors = List.of(
                new Distribution("Temurin", "temurin", true, true, false, List.of()),
                new Distribution("Liberica", "liberica", true, true, false, List.of()),
                new Distribution("SapMachine", "sap_machine", true, true, false, List.of()));

        assertEquals("liberica", JdkSource.find(vendors, new JdkRef("liberica-fx", 21)).orElseThrow().apiParameter());
        assertEquals("sap_machine", JdkSource.find(vendors, new JdkRef("sap-machine", 17)).orElseThrow().apiParameter());
        assertTrue(JdkSource.javafx(new JdkRef("liberica-fx", 21)));
        assertEquals("arm64", JdkSource.architecture(new JdkRef("temurin-arm64", 21)));
        assertEquals(Optional.empty(), JdkSource.find(vendors, new JdkRef("acme-builds", 8)));
    }
}
