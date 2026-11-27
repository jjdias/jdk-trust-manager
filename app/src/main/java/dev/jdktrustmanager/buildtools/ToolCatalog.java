package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.catalog.DiscoCatalog;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolVersions;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Version lists for each tool, cached like the JDK catalog and served stale when the network is down. */
public final class ToolCatalog {

    private final ToolHttp http;
    private final JsonStores stores;
    private final Path cacheFile;
    private final Clock clock;

    public ToolCatalog(ToolHttp http, JsonStores stores, Path cacheFile) {
        this(http, stores, cacheFile, Clock.systemUTC());
    }

    ToolCatalog(ToolHttp http, JsonStores stores, Path cacheFile, Clock clock) {
        this.http = http;
        this.stores = stores;
        this.cacheFile = cacheFile;
        this.clock = clock;
    }

    public synchronized DiscoCatalog.Load<List<ToolRelease>> releases(BuildTool tool, long maxAgeHours, boolean forceRefresh)
            throws IOException {
        Map<Tool, ToolCatalogDocument.Cached> cache = load();
        ToolCatalogDocument.Cached cached = cache.get(tool.tool());
        if (!forceRefresh && cached != null
                && !cached.fetchedAt().plus(Duration.ofHours(maxAgeHours)).isBefore(clock.instant())) {
            return new DiscoCatalog.Load<>(cached.releases(), false);
        }
        try {
            List<ToolRelease> releases = new ArrayList<>(tool.parse(new String(http.get(tool.versionListUrl()), StandardCharsets.UTF_8)));
            releases.sort((left, right) -> ToolVersions.NEWEST_FIRST.compare(left.version(), right.version()));
            cache.put(tool.tool(), new ToolCatalogDocument.Cached(clock.instant(), List.copyOf(releases)));
            stores.writeJson(cacheFile, new ToolCatalogDocument(cache));
            return new DiscoCatalog.Load<>(List.copyOf(releases), false);
        } catch (IOException ex) {
            if (cached != null) {
                return new DiscoCatalog.Load<>(cached.releases(), true);
            }
            throw ex;
        }
    }

    /** When the cached list for {@code tool} was fetched, or null when there is none. */
    public synchronized java.time.Instant fetchedAt(Tool tool) {
        try {
            ToolCatalogDocument.Cached cached = load().get(tool);
            return cached == null ? null : cached.fetchedAt();
        } catch (IOException ex) {
            return null;
        }
    }

    private Map<Tool, ToolCatalogDocument.Cached> load() throws IOException {
        Map<Tool, ToolCatalogDocument.Cached> cache = new EnumMap<>(Tool.class);
        if (Files.exists(cacheFile)) {
            try {
                ToolCatalogDocument document = stores.readJson(cacheFile, ToolCatalogDocument.class);
                if (document.tools() != null) {
                    cache.putAll(document.tools());
                }
            } catch (IOException ex) {
                // A damaged cache is treated as empty; the next fetch rewrites it.
            }
        }
        return cache;
    }
}
