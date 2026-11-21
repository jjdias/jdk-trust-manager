package dev.jdktrustmanager.install;

import dev.jdktrustmanager.catalog.MajorVersion;
import dev.jdktrustmanager.catalog.Slug;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where an existing JDK goes when it is adopted, worked out from its {@code release} file. A JDK that already sits at
 * its managed location is adopted in place; anything else is copied there and the original is left alone.
 */
public record AdoptPlan(String slug, int major, boolean javafx, String architecture, String javaVersion,
        String implementor, Path source, Path destination, boolean inPlace) {

    private static final Map<String, String> VENDORS = Map.ofEntries(
            Map.entry("eclipse adoptium", "temurin"),
            Map.entry("adoptopenjdk", "aoj"),
            Map.entry("azul systems, inc.", "zulu"),
            Map.entry("microsoft", "microsoft"),
            Map.entry("amazon.com inc.", "corretto"),
            Map.entry("bellsoft", "liberica"),
            Map.entry("sap se", "sap_machine"),
            Map.entry("oracle corporation", "oracle"),
            Map.entry("jetbrains s.r.o.", "jetbrains"),
            Map.entry("red hat, inc.", "redhat"),
            Map.entry("ibm corporation", "semeru"),
            Map.entry("international business machines corporation", "semeru"),
            Map.entry("graalvm community", "graalvm_community"));

    public static AdoptPlan of(Path source, Path root, Map<String, String> release) throws IOException {
        String javaVersion = release.getOrDefault("JAVA_VERSION", "");
        if (javaVersion.isBlank()) {
            throw new IOException("No JAVA_VERSION in " + source.resolve("release"));
        }
        int major = MajorVersion.fromJavaVersion(javaVersion);
        String implementor = release.getOrDefault("IMPLEMENTOR", "");
        boolean javafx = (" " + release.getOrDefault("MODULES", "") + " ").contains(" javafx.base ");
        String architecture = architecture(release.getOrDefault("OS_ARCH", ""));
        String slug = Slug.of(vendor(implementor), javafx, architecture);
        Path destination = Layout.destination(root, slug, major);
        boolean inPlace = destination.toAbsolutePath().normalize().equals(source.toAbsolutePath().normalize());
        return new AdoptPlan(slug, major, javafx, architecture, javaVersion, implementor, source, destination, inPlace);
    }

    /** Maps a release file's IMPLEMENTOR to the Foojay distribution name used for folder names and update checks. */
    static String vendor(String implementor) {
        String key = implementor == null ? "" : implementor.trim().toLowerCase(Locale.ROOT);
        String known = VENDORS.get(key);
        if (known != null) {
            return known;
        }
        String cleaned = key.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return cleaned.isEmpty() ? "jdk" : cleaned;
    }

    /** Release files say amd64 or x86_64; the catalog and folder names say x64. */
    static String architecture(String osArch) {
        String arch = osArch == null ? "" : osArch.trim().toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i586", "i686" -> "x86";
            default -> "x64";
        };
    }
}
