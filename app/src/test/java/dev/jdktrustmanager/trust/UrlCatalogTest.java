package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.UrlEntry;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UrlCatalogTest {

    @TempDir
    Path directory;

    @Test
    void onlyTestngIsBuiltInAndTextRoundTrips() throws Exception {
        Path file = directory.resolve("sources.json");
        new JsonStores().writeSources(file, new dev.jdktrustmanager.model.SourcesDocument(
                List.of(new UrlEntry("https://curl.se/ca/cacert.pem", true), new UrlEntry(UrlCatalog.TESTNG, true)),
                List.of()));
        List<UrlEntry> loaded = new UrlCatalog().load(file);
        assertEquals(List.of(new UrlEntry(UrlCatalog.TESTNG, true)), loaded);
        assertThrows(IllegalArgumentException.class, () -> UrlCatalog.normalize("http://example.com"));

        List<UrlEntry> edited = UrlCatalog.add(loaded, "https://cm.example");
        edited = UrlCatalog.enabled(edited, 1, false);
        String text = UrlCatalog.exportText(edited);
        assertTrue(text.startsWith("https://testng.org\n"));
        assertTrue(text.contains("# https://cm.example"));
        List<UrlEntry> imported = UrlCatalog.importText(text);
        assertEquals(edited, imported);
        assertEquals(1, UrlCatalog.enabledOnly(imported).size());
        assertFalse(imported.get(1).enabled());

        TrustSync sync = new TrustSync();
        String pem = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----";
        assertEquals(1, sync.unique(List.of(pem, pem)).size());
    }

    @Test
    void importMergesIntoTheExistingListInsteadOfReplacingIt() {
        List<UrlEntry> existing = List.of(
                new UrlEntry(UrlCatalog.TESTNG, true),
                new UrlEntry("https://cm.example", false));
        List<UrlEntry> imported = List.of(
                new UrlEntry("https://CM.example", true),
                new UrlEntry("https://ci.example", false));

        List<UrlEntry> merged = UrlCatalog.merge(existing, imported);

        assertEquals(List.of(
                new UrlEntry(UrlCatalog.TESTNG, true),
                new UrlEntry("https://cm.example", false),
                new UrlEntry("https://ci.example", false)), merged);
    }
}
