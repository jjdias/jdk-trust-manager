package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Installation(
        String vendorSlug,
        int major,
        String discoPackageId,
        String javaVersion,
        boolean javafxBundled,
        String path,
        String architecture,
        String implementor,
        String releaseVersion,
        String zipSha256,
        Instant installedAt,
        InstallSource source,
        boolean active,
        Instant lastTrustSync,
        String overlayFingerprint,
        boolean pinned,
        String notes) {
}
