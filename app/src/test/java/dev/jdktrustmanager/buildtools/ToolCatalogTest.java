package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCatalogTest {

    @TempDir
    Path directory;

    private final AtomicInteger calls = new AtomicInteger();
    private boolean failing;

    private final ToolHttp http = url -> {
        calls.incrementAndGet();
        if (failing) {
            throw new IOException("offline");
        }
        return ParsersTest.fixture("maven-metadata.xml").getBytes(StandardCharsets.UTF_8);
    };

    private ToolCatalog catalogAt(Instant now) {
        return new ToolCatalog(http, new JsonStores(), directory.resolve("tool-catalog.json"), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void freshCacheMakesNoRequest() throws Exception {
        Instant start = Instant.parse("2026-11-27T10:00:00Z");
        catalogAt(start).releases(BuildTools.MAVEN, 24, false);
        calls.set(0);

        var load = catalogAt(start.plus(Duration.ofHours(1))).releases(BuildTools.MAVEN, 24, false);

        assertEquals(0, calls.get());
        assertFalse(load.stale());
        assertEquals("4.0.0-rc-7", load.value().get(0).version());
    }

    @Test
    void expiredCacheRefetchesOnce() throws Exception {
        Instant start = Instant.parse("2026-11-27T10:00:00Z");
        catalogAt(start).releases(BuildTools.MAVEN, 24, false);
        calls.set(0);

        catalogAt(start.plus(Duration.ofHours(25))).releases(BuildTools.MAVEN, 24, false);

        assertEquals(1, calls.get());
    }

    @Test
    void offlineWithACacheReturnsItMarkedStale() throws Exception {
        Instant start = Instant.parse("2026-11-27T10:00:00Z");
        catalogAt(start).releases(BuildTools.MAVEN, 24, false);
        failing = true;

        var load = catalogAt(start.plus(Duration.ofHours(30))).releases(BuildTools.MAVEN, 24, false);

        assertTrue(load.stale());
        assertTrue(load.value().stream().anyMatch(release -> release.version().equals("3.3.9")));
    }

    @Test
    void offlineWithoutACacheThrows() {
        failing = true;

        assertThrows(IOException.class, () -> catalogAt(Instant.now()).releases(BuildTools.MAVEN, 24, false));
    }

    @Test
    void cachesAreKeptPerTool() throws Exception {
        Instant start = Instant.parse("2026-11-27T10:00:00Z");
        catalogAt(start).releases(BuildTools.MAVEN, 24, false);
        calls.set(0);

        assertThrows(IOException.class, () -> new ToolCatalog(url -> { throw new IOException("offline"); }, new JsonStores(),
                directory.resolve("tool-catalog.json"), Clock.fixed(start, ZoneOffset.UTC)).releases(BuildTools.ANT, 24, false));
    }
}
