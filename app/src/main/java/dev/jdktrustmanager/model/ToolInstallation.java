package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** One installed version of a build tool, stored in tools.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolInstallation(
        Tool tool,
        String version,
        String path,
        ToolSource source,
        Verification verification,
        Instant installedAt,
        boolean active,
        boolean pinned,
        String notes) {
}
