package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdoptPlanTest {

    @TempDir
    Path directory;

    @Test
    void mapsReleaseFileValuesToTheCatalogNames() throws Exception {
        Path root = directory.resolve("Java");
        AdoptPlan plan = AdoptPlan.of(directory.resolve("zulu17"), root, Map.of(
                "JAVA_VERSION", "17.0.13",
                "IMPLEMENTOR", "Azul Systems, Inc.",
                "OS_ARCH", "amd64",
                "MODULES", "java.base javafx.base javafx.controls"));
        assertEquals("zulu-fx", plan.slug());
        assertEquals(17, plan.major());
        assertTrue(plan.javafx());
        assertEquals("x64", plan.architecture());
        assertEquals(root.resolve("zulu-fx-17"), plan.destination());
        assertFalse(plan.inPlace());
    }

    @Test
    void knowsCommonVendorsAndCleansUpUnknownOnes() {
        assertEquals("temurin", AdoptPlan.vendor("Eclipse Adoptium"));
        assertEquals("sap_machine", AdoptPlan.vendor("SAP SE"));
        assertEquals("corretto", AdoptPlan.vendor("Amazon.com Inc."));
        assertEquals("acme-builds", AdoptPlan.vendor("  ACME Builds!  "));
        assertEquals("jdk", AdoptPlan.vendor(""));
        assertEquals("x64", AdoptPlan.architecture("x86_64"));
        assertEquals("arm64", AdoptPlan.architecture("aarch64"));
    }

    @Test
    void aJdkAlreadyAtItsManagedFolderIsAdoptedInPlace() throws Exception {
        Path root = directory.resolve("Java");
        Path home = fakeJdk(root.resolve("temurin-21"), "21.0.13");
        AdoptPlan plan = new Adopter().plan(home, root);
        assertTrue(plan.inPlace());

        Installation adopted = new Adopter().adopt(plan, root, null);

        assertEquals(home.toString(), adopted.path());
        assertEquals(InstallSource.ADOPTED, adopted.source());
        assertEquals("x64", adopted.architecture());
        assertTrue(Files.isRegularFile(home.resolve("bin").resolve("java.exe")));
    }

    @Test
    void anOutsideJdkIsCopiedAndTheOriginalKept() throws Exception {
        Path root = directory.resolve("Java");
        Path outside = fakeJdk(directory.resolve("Program Files").resolve("jdk-21"), "21.0.13");
        AdoptPlan plan = new Adopter().plan(outside, root);

        Installation adopted = new Adopter().adopt(plan, root, null);

        assertEquals(root.resolve("temurin-21").toString(), adopted.path());
        assertTrue(JdkScanner.isHome(Path.of(adopted.path())));
        assertTrue(JdkScanner.isHome(outside));
    }

    @Test
    void neverCopiesIntoAFolderThatAlreadyExists() throws Exception {
        Path root = directory.resolve("Java");
        Files.createDirectories(root.resolve("temurin-21"));
        Path outside = fakeJdk(directory.resolve("elsewhere").resolve("jdk-21"), "21.0.13");
        AdoptPlan plan = new Adopter().plan(outside, root);

        IOException error = assertThrows(IOException.class, () -> new Adopter().adopt(plan, root, null));

        assertTrue(error.getMessage().contains("already exists"));
    }

    private static Path fakeJdk(Path home, String version) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"),
                "JAVA_VERSION=\"" + version + "\"\nIMPLEMENTOR=\"Eclipse Adoptium\"\nOS_ARCH=\"x86_64\"\n");
        Files.write(home.resolve("bin").resolve("java.exe"), new byte[] {1});
        return home;
    }
}
