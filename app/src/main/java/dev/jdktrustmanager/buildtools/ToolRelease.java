package dev.jdktrustmanager.buildtools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.jdktrustmanager.model.Tool;

/** One published version. {@code sha256} is only known up front for Gradle. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolRelease(Tool tool, String version, String zipUrl, String sha256, boolean preRelease) {
}
