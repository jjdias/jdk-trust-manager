package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CachedPackageList(
        String distribution,
        int major,
        boolean javafxBundled,
        boolean latestOnly,
        boolean includeGraalvm,
        List<CatalogPackage> packages) {
}
