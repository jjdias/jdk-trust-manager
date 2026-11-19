package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogPackage(
        String id,
        String distribution,
        int major,
        String javaVersion,
        String archiveType,
        String architecture,
        boolean javafxBundled,
        boolean freeUseInProduction,
        boolean directlyDownloadable,
        String releaseStatus,
        String termOfSupport,
        String tckTested,
        String filename,
        long size,
        boolean buildOfGraalvm) {

    public CatalogPackage {
        if ("amd64".equalsIgnoreCase(architecture)) {
            architecture = "x64";
        }
    }
}
