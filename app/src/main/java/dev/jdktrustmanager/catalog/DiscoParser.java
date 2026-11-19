package dev.jdktrustmanager.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class DiscoParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Distribution> distributions(String json) throws IOException {
        List<Distribution> parsed = new ArrayList<>();
        for (JsonNode node : mapper.readTree(json).path("result")) {
            if (!node.path("maintained").asBoolean(false) || !node.path("available").asBoolean(false)) {
                continue;
            }
            List<String> versions = new ArrayList<>();
            for (JsonNode version : node.path("versions")) {
                versions.add(version.asText());
            }
            parsed.add(new Distribution(
                    node.path("name").asText(),
                    node.path("api_parameter").asText(),
                    true,
                    true,
                    node.path("build_of_graalvm").asBoolean(false),
                    List.copyOf(versions)));
        }
        return List.copyOf(parsed);
    }

    public List<CatalogPackage> packages(String json, boolean includeGraalvm) throws IOException {
        List<CatalogPackage> parsed = new ArrayList<>();
        for (JsonNode node : mapper.readTree(json).path("result")) {
            boolean free = node.path("free_use_in_production").asBoolean(false);
            boolean downloadable = node.path("directly_downloadable").asBoolean(false);
            boolean graalvm = node.path("build_of_graalvm").asBoolean(false);
            if (!free || !downloadable || (graalvm && !includeGraalvm)) {
                continue;
            }
            String javaVersion = node.path("java_version").asText();
            int major = node.hasNonNull("java_version")
                    ? MajorVersion.fromJavaVersion(javaVersion)
                    : node.path("major_version").asInt();
            parsed.add(new CatalogPackage(
                    node.path("id").asText(),
                    node.path("distribution").asText(),
                    major,
                    javaVersion,
                    node.path("archive_type").asText(),
                    node.path("architecture").asText(),
                    node.path("javafx_bundled").asBoolean(false),
                    free,
                    downloadable,
                    node.path("release_status").asText(),
                    node.path("term_of_support").asText(),
                    node.path("tck_tested").asText(),
                    node.path("filename").asText(),
                    node.path("size").asLong(),
                    graalvm));
        }
        return List.copyOf(parsed);
    }

    public PackageDetail detail(String json) throws IOException {
        JsonNode node = mapper.readTree(json).path("result");
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("Disco package detail has no result");
        }
        JsonNode first = node.get(0);
        String checksum = first.path("checksum").asText("");
        String checksumType = first.path("checksum_type").asText("");
        if (checksum.isBlank() || !"sha256".equals(checksumType)) {
            throw new IllegalArgumentException("Disco package detail requires a sha256 checksum");
        }
        return new PackageDetail(first.path("direct_download_uri").asText(), checksum, checksumType);
    }
}
