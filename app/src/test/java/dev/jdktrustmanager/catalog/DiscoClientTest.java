package dev.jdktrustmanager.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoClientTest {

    @Test
    void parsesFixturesOffline() throws Exception {
        String distributions = resource("distributions.json");
        String packages = resource("packages.json");
        String detail = resource("detail.json");
        List<String> calls = new ArrayList<>();
        DiscoClient client = new DiscoClient(path -> {
            calls.add(path);
            if (path.startsWith("/distributions")) {
                return distributions;
            }
            if (path.startsWith("/packages")) {
                return packages;
            }
            if (path.startsWith("/ids/")) {
                return detail;
            }
            throw new IOException(path);
        });

        List<Distribution> vendors = client.listDistributions();
        assertEquals(List.of("temurin", "sap_machine"), vendors.stream().map(Distribution::apiParameter).toList());
        assertEquals(List.of("/distributions"), calls);
        assertEquals("sap-machine", Slug.of(vendors.get(1).apiParameter(), false, "x64"));
        assertEquals("liberica-fx", Slug.of("liberica", true, "x64"));
        assertEquals("temurin-arm64", Slug.of("temurin", false, "arm64"));
        assertEquals("zulu-fx-arm64", Slug.of("zulu", true, "arm64"));

        List<CatalogPackage> latest = client.listPackages("temurin", 21, false, false);
        assertTrue(calls.get(1).contains("operating_system=windows"));
        assertTrue(calls.get(1).contains("archive_type=zip"));
        assertTrue(calls.get(1).contains("package_type=jdk"));
        assertTrue(calls.get(1).contains("architecture=x64"));
        assertTrue(calls.get(1).contains("release_status=ga"));
        assertTrue(calls.get(1).contains("latest=available"));
        assertEquals(List.of("temurin-21", "corretto-8", "temurin-8"), latest.stream().map(CatalogPackage::id).toList());
        assertEquals("x64", latest.get(0).architecture());
        assertEquals(8, latest.get(1).major());
        assertEquals(8, latest.get(2).major());
        assertFalse(latest.stream().anyMatch(pkg -> "not-free".equals(pkg.id())));

        CatalogPackage javafx = client.listPackages("liberica", 21, true, false).get(0);
        assertEquals("liberica-fx", Slug.of(javafx.distribution(), javafx.javafxBundled(), javafx.architecture()));

        assertEquals(List.of("graal"), client.listPackages("graalvm", 21, false, true).stream()
                .map(CatalogPackage::id)
                .filter(id -> id.equals("graal"))
                .toList());

        client.listPackageVersions("temurin", 21, false, false);
        String picker = calls.get(calls.size() - 1);
        assertFalse(picker.contains("latest="));

        PackageDetail resolved = client.packageDetail("temurin-21");
        assertEquals("sha256", resolved.checksumType());
        assertFalse(resolved.directDownloadUri().contains("redirect"));
        assertThrows(IllegalArgumentException.class, () -> new DiscoParser().detail(resource("detail-bad.json")));
    }

    @Test
    void staleCacheAndZipGate(@TempDir Path directory) throws Exception {
        Path catalog = directory.resolve("catalog.json");
        Path zipCache = directory.resolve("cache");
        Files.createDirectories(zipCache);
        String packages = resource("packages.json");
        String distributions = resource("distributions.json");
        DiscoClient client = new DiscoClient(path -> {
            if (path.startsWith("/distributions")) {
                return distributions;
            }
            if (path.startsWith("/packages")) {
                return packages;
            }
            throw new IOException("down");
        });
        DiscoCatalog disco = new DiscoCatalog(client, new dev.jdktrustmanager.persist.JsonStores(), catalog);
        DiscoCatalog.Load<List<CatalogPackage>> first = disco.packages("temurin", 21, false, true, false, 24, true);
        assertFalse(first.stale());
        assertEquals("21.0.13+9", first.value().get(0).javaVersion());

        DiscoClient failing = new DiscoClient(path -> {
            throw new IOException("down");
        });
        DiscoCatalog offline = new DiscoCatalog(failing, new dev.jdktrustmanager.persist.JsonStores(), catalog);
        DiscoCatalog.Load<List<CatalogPackage>> cached = offline.packages("temurin", 21, false, true, false, 24, true);
        assertTrue(cached.stale());
        assertEquals("temurin-21", cached.value().get(0).id());
        assertFalse(offline.installAllowed(first.value().get(0).id(), zipCache));
        Files.writeString(zipCache.resolve("abc.zip"), "zip");
        assertTrue(offline.installAllowed("abc", zipCache));
    }

    private static String resource(String name) throws IOException {
        try (var input = DiscoClientTest.class.getResourceAsStream("/disco/" + name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
