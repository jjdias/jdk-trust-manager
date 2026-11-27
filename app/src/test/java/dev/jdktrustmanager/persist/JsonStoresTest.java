package dev.jdktrustmanager.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.EnvironmentScope;
import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Settings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStoresTest {

    private final JsonStores stores = new JsonStores();

    @TempDir
    Path directory;

    @Test
    void installationRoundTripsAndIgnoresUnknownFields() throws Exception {
        Installation installation = new Installation(
                "temurin",
                21,
                "24b75c034bab89dfde162735bcae3b96",
                "21.0.13+9",
                false,
                "C:\\Java\\temurin-21",
                "x64",
                "Eclipse Adoptium",
                "21.0.13",
                "a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9",
                Instant.parse("2026-11-19T12:00:00Z"),
                InstallSource.FOOJAY,
                true,
                null,
                null,
                true,
                "local note");
        Path file = directory.resolve("installations.json");
        stores.writeInstallations(file, List.of(installation));

        String withUnknown = Files.readString(file).replace(
                "{",
                "{\n  \"futureField\" : \"ignore-me\",");
        Files.writeString(file, withUnknown);

        assertEquals(List.of(installation), stores.readInstallations(file));
        assertFalse(Files.exists(file.resolveSibling("installations.json.tmp")));
    }

    @Test
    void toolsRoundTripAndMissingFileIsEmpty() throws Exception {
        var rows = List.of(
                new dev.jdktrustmanager.model.ToolInstallation(dev.jdktrustmanager.model.Tool.MAVEN, "3.3.9",
                        "C:\\Java\\build-tools\\maven-3.3.9", dev.jdktrustmanager.model.ToolSource.DOWNLOADED,
                        dev.jdktrustmanager.model.Verification.SHA1, Instant.parse("2026-11-27T12:00:00Z"), true, false, ""),
                new dev.jdktrustmanager.model.ToolInstallation(dev.jdktrustmanager.model.Tool.GRADLE, "8.10.2",
                        "C:\\Java\\build-tools\\gradle-8.10.2", dev.jdktrustmanager.model.ToolSource.ADOPTED,
                        dev.jdktrustmanager.model.Verification.NONE, null, false, true, "note"));
        Path file = directory.resolve("tools.json");

        stores.writeTools(file, rows);

        assertEquals(rows, stores.readTools(file));
        assertEquals(List.of(), stores.readTools(directory.resolve("missing.json")));
    }

    @Test
    void packageWithFreeUseFalseRoundTrips() throws Exception {
        CatalogPackage rejected = new CatalogPackage(
                "pkg-1",
                "oracle",
                21,
                "21.0.5",
                "zip",
                "amd64",
                false,
                false,
                true,
                "ga",
                "lts",
                "unknown",
                "jdk.zip",
                100,
                false);
        Path file = directory.resolve("catalog.json");
        stores.writeCatalog(file, new dev.jdktrustmanager.model.CatalogDocument(
                Instant.parse("2026-11-19T12:00:00Z"),
                List.of(rejected),
                List.of(),
                List.of(),
                false));

        CatalogPackage read = stores.readCatalog(file).packages().get(0);
        assertFalse(read.freeUseInProduction());
        assertEquals("x64", read.architecture());
        assertEquals(rejected, read);
    }

    @Test
    void settingsRoundTrip() throws Exception {
        Settings settings = new Settings(
                "C:\\Java",
                EnvironmentScope.USER,
                24,
                true,
                false,
                false,
                false,
                false,
                false);
        Path file = directory.resolve("settings.json");
        stores.writeSettings(file, settings);
        assertEquals(settings, stores.readSettings(file));
    }
}
