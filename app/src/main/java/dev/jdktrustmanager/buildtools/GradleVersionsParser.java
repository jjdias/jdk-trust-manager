package dev.jdktrustmanager.buildtools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolVersions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads {@code https://services.gradle.org/versions/all}. Snapshots, nightlies, and broken versions are dropped. */
public final class GradleVersionsParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GradleVersionsParser() {
    }

    public static List<ToolRelease> releases(String json) throws IOException {
        List<ToolRelease> releases = new ArrayList<>();
        for (JsonNode node : MAPPER.readTree(json)) {
            if (flag(node, "snapshot") || flag(node, "nightly") || flag(node, "releaseNightly") || flag(node, "broken")) {
                continue;
            }
            String version = node.path("version").asText("");
            String url = node.path("downloadUrl").asText("");
            if (version.isEmpty() || url.isEmpty()) {
                continue;
            }
            boolean preRelease = flag(node, "activeRc")
                    || !node.path("rcFor").asText("").isEmpty()
                    || !node.path("milestoneFor").asText("").isEmpty()
                    || ToolVersions.isPreRelease(version);
            String checksum = node.path("checksum").asText("");
            releases.add(new ToolRelease(Tool.GRADLE, version, url, checksum.isEmpty() ? null : checksum.toLowerCase(), preRelease));
        }
        return releases;
    }

    private static boolean flag(JsonNode node, String name) {
        return node.path(name).asBoolean(false);
    }
}
