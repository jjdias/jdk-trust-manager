package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.SourcesDocument;
import dev.jdktrustmanager.model.UrlEntry;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UrlCatalog {

    public static final String TESTNG = "https://testng.org";
    private static final String MOZILLA = "https://curl.se/ca/cacert.pem";

    private final JsonStores stores = new JsonStores();

    public List<UrlEntry> load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            List<UrlEntry> seeded = List.of(new UrlEntry(TESTNG, true));
            save(file, seeded);
            return seeded;
        }
        SourcesDocument document = stores.readSources(file);
        List<UrlEntry> urls = new ArrayList<>();
        if (document.urls() != null) {
            for (UrlEntry entry : document.urls()) {
                if (entry.url() != null && !entry.url().isBlank() && !MOZILLA.equals(entry.url().trim())) {
                    urls.add(new UrlEntry(entry.url().trim(), entry.enabled()));
                }
            }
        }
        if (urls.isEmpty()) {
            urls.add(new UrlEntry(TESTNG, true));
        }
        save(file, urls);
        return urls;
    }

    public void save(Path file, List<UrlEntry> urls) throws IOException {
        SourcesDocument existing = Files.isRegularFile(file) ? stores.readSources(file) : new SourcesDocument(List.of(), List.of());
        stores.writeSources(file, new SourcesDocument(List.copyOf(urls), existing.certificates() == null ? List.of() : existing.certificates()));
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        String text = raw.trim();
        URI uri = URI.create(text);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Use an https URL");
        }
        return text;
    }

    public static List<UrlEntry> add(List<UrlEntry> urls, String raw) {
        String url = normalize(raw);
        List<UrlEntry> next = new ArrayList<>(urls);
        next.removeIf(entry -> entry.url().equalsIgnoreCase(url));
        next.add(new UrlEntry(url, true));
        return next;
    }

    public static List<UrlEntry> replace(List<UrlEntry> urls, int index, String raw) {
        String url = normalize(raw);
        List<UrlEntry> next = new ArrayList<>(urls);
        boolean enabled = next.get(index).enabled();
        next.set(index, new UrlEntry(url, enabled));
        return next;
    }

    public static List<UrlEntry> enabled(List<UrlEntry> urls, int index, boolean enabled) {
        List<UrlEntry> next = new ArrayList<>(urls);
        next.set(index, new UrlEntry(next.get(index).url(), enabled));
        return next;
    }

    public static List<UrlEntry> remove(List<UrlEntry> urls, int index) {
        List<UrlEntry> next = new ArrayList<>(urls);
        next.remove(index);
        return next;
    }

    public static List<UrlEntry> merge(List<UrlEntry> existing, List<UrlEntry> imported) {
        List<UrlEntry> next = new ArrayList<>(existing);
        for (UrlEntry entry : imported) {
            if (next.stream().noneMatch(known -> known.url().equalsIgnoreCase(entry.url()))) {
                next.add(entry);
            }
        }
        return next;
    }

    public static List<UrlEntry> enabledOnly(List<UrlEntry> urls) {
        List<UrlEntry> enabled = new ArrayList<>();
        for (UrlEntry entry : urls) {
            if (entry.enabled()) {
                enabled.add(entry);
            }
        }
        return enabled;
    }

    public static String exportText(List<UrlEntry> urls) {
        StringBuilder text = new StringBuilder();
        for (UrlEntry entry : urls) {
            if (!entry.enabled()) {
                text.append("# ");
            }
            text.append(entry.url()).append('\n');
        }
        return text.toString();
    }

    public static List<UrlEntry> importText(String text) {
        List<UrlEntry> urls = new ArrayList<>();
        if (text == null) {
            return urls;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean enabled = !trimmed.startsWith("#");
            String url = enabled ? trimmed : trimmed.substring(1).trim();
            if (url.isEmpty()) {
                continue;
            }
            urls.add(new UrlEntry(normalize(url), enabled));
        }
        return urls;
    }
}
