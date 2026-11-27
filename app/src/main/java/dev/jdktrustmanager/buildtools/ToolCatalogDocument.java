package dev.jdktrustmanager.buildtools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jdktrustmanager.model.Tool;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** tool-catalog.json: the last version list fetched for each tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCatalogDocument(Map<Tool, Cached> tools) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cached(Instant fetchedAt, List<ToolRelease> releases) {
    }
}
