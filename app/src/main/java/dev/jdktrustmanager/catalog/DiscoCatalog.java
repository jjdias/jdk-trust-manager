package dev.jdktrustmanager.catalog;

import dev.jdktrustmanager.model.CachedPackageList;
import dev.jdktrustmanager.model.CatalogDocument;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DiscoCatalog {

    private final DiscoClient client;
    private final JsonStores stores;
    private final Path catalogFile;

    public DiscoCatalog(DiscoClient client, JsonStores stores, Path catalogFile) {
        this.client = client;
        this.stores = stores;
        this.catalogFile = catalogFile;
    }

    public Load<List<Distribution>> distributions(long maxAgeHours, boolean forceRefresh) throws IOException {
        CatalogDocument cached = read();
        if (!forceRefresh && fresh(cached, maxAgeHours) && !cached.distributions().isEmpty()) {
            return new Load<>(cached.distributions(), false);
        }
        try {
            List<Distribution> live = client.listDistributions();
            write(cached.packages(), live, cached.packageLists(), false);
            return new Load<>(live, false);
        } catch (IOException ex) {
            if (cached.distributions().isEmpty() && cached.fetchedAt() == null) {
                throw ex;
            }
            write(cached.packages(), cached.distributions(), cached.packageLists(), true);
            return new Load<>(cached.distributions(), true);
        }
    }

    public Load<List<CatalogPackage>> packages(
            String distribution,
            int major,
            boolean javafxBundled,
            boolean latestOnly,
            boolean includeGraalvm,
            long maxAgeHours,
            boolean forceRefresh) throws IOException {
        CatalogDocument cached = read();
        CachedPackageList hit = find(cached, distribution, major, javafxBundled, latestOnly, includeGraalvm);
        if (!forceRefresh && fresh(cached, maxAgeHours) && hit != null) {
            return new Load<>(hit.packages(), false);
        }
        try {
            List<CatalogPackage> live = latestOnly
                    ? client.listPackages(distribution, major, javafxBundled, includeGraalvm)
                    : client.listPackageVersions(distribution, major, javafxBundled, includeGraalvm);
            CachedPackageList saved = new CachedPackageList(
                    distribution, major, javafxBundled, latestOnly, includeGraalvm, live);
            write(cached.packages(), cached.distributions(), replace(cached.packageLists(), saved), false);
            return new Load<>(live, false);
        } catch (IOException ex) {
            if (hit == null) {
                throw ex;
            }
            write(cached.packages(), cached.distributions(), cached.packageLists(), true);
            return new Load<>(hit.packages(), true);
        }
    }

    public boolean installAllowed(String sha256, Path zipCacheDirectory) {
        if (sha256 == null || sha256.isBlank()) {
            return false;
        }
        return Files.isRegularFile(zipCacheDirectory.resolve(sha256 + ".zip"));
    }

    private CatalogDocument read() throws IOException {
        if (Files.notExists(catalogFile)) {
            return empty();
        }
        CatalogDocument document = stores.readCatalog(catalogFile);
        return new CatalogDocument(
                document.fetchedAt(),
                document.packages() == null ? List.of() : document.packages(),
                document.distributions() == null ? List.of() : document.distributions(),
                document.packageLists() == null ? List.of() : document.packageLists(),
                document.stale());
    }

    private void write(
            List<CatalogPackage> packages,
            List<Distribution> distributions,
            List<CachedPackageList> packageLists,
            boolean stale) throws IOException {
        stores.writeCatalog(catalogFile, new CatalogDocument(Instant.now(), packages, distributions, packageLists, stale));
    }

    private static boolean fresh(CatalogDocument document, long maxAgeHours) {
        if (document.fetchedAt() == null) {
            return false;
        }
        return !document.fetchedAt().isBefore(Instant.now().minus(Duration.ofHours(maxAgeHours)));
    }

    private static CachedPackageList find(
            CatalogDocument document,
            String distribution,
            int major,
            boolean javafxBundled,
            boolean latestOnly,
            boolean includeGraalvm) {
        for (CachedPackageList list : document.packageLists()) {
            if (list.distribution().equals(distribution)
                    && list.major() == major
                    && list.javafxBundled() == javafxBundled
                    && list.latestOnly() == latestOnly
                    && list.includeGraalvm() == includeGraalvm) {
                return list;
            }
        }
        return null;
    }

    private static List<CachedPackageList> replace(List<CachedPackageList> current, CachedPackageList saved) {
        List<CachedPackageList> next = new ArrayList<>();
        for (CachedPackageList list : current) {
            if (!(list.distribution().equals(saved.distribution())
                    && list.major() == saved.major()
                    && list.javafxBundled() == saved.javafxBundled()
                    && list.latestOnly() == saved.latestOnly()
                    && list.includeGraalvm() == saved.includeGraalvm())) {
                next.add(list);
            }
        }
        next.add(saved);
        return List.copyOf(next);
    }

    private static CatalogDocument empty() {
        return new CatalogDocument(null, List.of(), List.of(), List.of(), false);
    }

    public record Load<T>(T value, boolean stale) {
    }
}
