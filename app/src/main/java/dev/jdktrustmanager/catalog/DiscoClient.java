package dev.jdktrustmanager.catalog;

import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DiscoClient {

    private final DiscoTransport transport;
    private final DiscoParser parser = new DiscoParser();

    public DiscoClient(DiscoTransport transport) {
        this.transport = transport;
    }

    public static DiscoClient live() {
        return new DiscoClient(new HttpDiscoTransport());
    }

    public List<Distribution> listDistributions() throws IOException {
        return parser.distributions(transport.get("/distributions"));
    }

    public List<CatalogPackage> listPackages(String distribution, int major, boolean javafxBundled, boolean includeGraalvm)
            throws IOException {
        return packages(distribution, major, javafxBundled, includeGraalvm, true);
    }

    public List<CatalogPackage> listPackageVersions(
            String distribution, int major, boolean javafxBundled, boolean includeGraalvm) throws IOException {
        return packages(distribution, major, javafxBundled, includeGraalvm, false);
    }

    public PackageDetail packageDetail(String id) throws IOException {
        return parser.detail(transport.get("/ids/" + URLEncoder.encode(id, StandardCharsets.UTF_8)));
    }

    private List<CatalogPackage> packages(
            String distribution, int major, boolean javafxBundled, boolean includeGraalvm, boolean latestOnly)
            throws IOException {
        String path = "/packages?distribution=" + encode(distribution)
                + "&version=" + major
                + "&operating_system=windows"
                + "&archive_type=zip"
                + "&package_type=jdk"
                + "&architecture=x64"
                + "&release_status=ga";
        if (latestOnly) {
            path = path + "&latest=available";
        }
        List<CatalogPackage> matched = new ArrayList<>();
        for (CatalogPackage candidate : parser.packages(transport.get(path), includeGraalvm)) {
            if (candidate.javafxBundled() == javafxBundled) {
                matched.add(candidate);
            }
        }
        return List.copyOf(matched);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
