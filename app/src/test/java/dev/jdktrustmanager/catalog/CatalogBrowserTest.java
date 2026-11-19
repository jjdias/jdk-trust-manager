package dev.jdktrustmanager.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.EnvironmentScope;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogBrowserTest {

    @TempDir
    Path directory;

    @Test
    void temurin21CanBeInstalledAndStaleCacheStillRenders() throws Exception {
        String distributions = resource("distributions.json");
        String packages = resource("packages.json");
        Settings settings = new Settings(directory.toString(), EnvironmentScope.USER, 24, true, false, false, false, false, false);
        DiscoClient client = new DiscoClient(path -> path.startsWith("/distributions") ? distributions : packages);
        CatalogBrowser browser = new CatalogBrowser(new DiscoCatalog(client, new JsonStores(), directory.resolve("catalog.json")));
        Distribution temurin = browser.vendors(settings, true).get(0);
        assertEquals("temurin", temurin.apiParameter());
        assertTrue(browser.majors(temurin, settings).containsKey(21));
        CatalogBrowser.Card card = browser.latest(temurin, 21, settings, false, true);
        assertEquals("21.0.13+9", card.pkg().javaVersion());
        assertEquals("unknown", card.pkg().tckTested());
        assertFalse(card.installed());
        assertTrue(CatalogBrowser.matches(card, "tem", 21, true, false, false, false, java.util.List.of()));

        DiscoClient offline = new DiscoClient(path -> {
            throw new java.io.IOException("offline");
        });
        CatalogBrowser cached = new CatalogBrowser(new DiscoCatalog(offline, new JsonStores(), directory.resolve("catalog.json")));
        CatalogBrowser.Card stale = cached.latest(temurin, 21, settings, false, true);
        assertTrue(stale.stale());
        assertEquals("21.0.13+9", stale.pkg().javaVersion());
        assertTrue(Files.isRegularFile(directory.resolve("catalog.json")));
    }

    @Test
    void showJavafxRevealsLibericaFromTheCache() throws Exception {
        String packages = resource("packages.json");
        Settings shown = new Settings(directory.toString(), EnvironmentScope.USER, 24, true, false, false, false, true, false);
        DiscoClient client = new DiscoClient(path -> packages);
        Path cache = directory.resolve("catalog.json");
        Distribution liberica = new Distribution("Liberica", "liberica", true, true, false, java.util.List.of("21.0.13+11"));
        new CatalogBrowser(new DiscoCatalog(client, new JsonStores(), cache)).latest(liberica, 21, shown, true, true);
        DiscoClient offline = new DiscoClient(path -> {
            throw new java.io.IOException("offline");
        });
        CatalogBrowser.Card fx = new CatalogBrowser(new DiscoCatalog(offline, new JsonStores(), cache))
                .latest(liberica, 21, shown, true, true);
        assertTrue(fx.stale());
        assertEquals("liberica-fx", fx.pkg().id());
        assertTrue(fx.pkg().javafxBundled());
    }

    private static String resource(String name) throws java.io.IOException {
        try (var input = CatalogBrowserTest.class.getResourceAsStream("/disco/" + name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
