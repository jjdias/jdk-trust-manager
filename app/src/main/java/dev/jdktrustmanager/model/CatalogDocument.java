package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogDocument(
        Instant fetchedAt,
        List<CatalogPackage> packages,
        List<Distribution> distributions,
        List<CachedPackageList> packageLists,
        boolean stale) {
}
