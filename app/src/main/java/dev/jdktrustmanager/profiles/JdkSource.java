package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.catalog.Slug;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.JdkRef;
import java.util.List;
import java.util.Optional;

/** Finds which catalog vendor installs a profile's JDK, so a missing one can be installed again. */
public final class JdkSource {

    private JdkSource() {
    }

    /** The distribution whose folder name for this JavaFX flavour and architecture is {@code jdk.vendorSlug()}. */
    public static Optional<Distribution> find(List<Distribution> vendors, JdkRef jdk) {
        boolean javafx = javafx(jdk);
        String architecture = architecture(jdk);
        return vendors.stream()
                .filter(vendor -> Slug.of(vendor.apiParameter(), javafx, architecture).equals(jdk.vendorSlug()))
                .findFirst();
    }

    public static boolean javafx(JdkRef jdk) {
        return base(jdk).endsWith("-fx");
    }

    public static String architecture(JdkRef jdk) {
        return jdk.vendorSlug().endsWith("-arm64") ? "arm64" : "x64";
    }

    private static String base(JdkRef jdk) {
        String slug = jdk.vendorSlug();
        return slug.endsWith("-arm64") ? slug.substring(0, slug.length() - "-arm64".length()) : slug;
    }
}
