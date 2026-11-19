package dev.jdktrustmanager.catalog;

import dev.jdktrustmanager.install.Layout;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogBrowser {

    public record Card(
            String vendor,
            String apiParameter,
            int major,
            CatalogPackage pkg,
            boolean installed,
            boolean stale) {
    }

    private final DiscoCatalog catalog;

    public CatalogBrowser(DiscoCatalog catalog) {
        this.catalog = catalog;
    }

    public List<Distribution> vendors(Settings settings, boolean forceRefresh) throws IOException {
        List<Distribution> vendors = new ArrayList<>();
        for (Distribution distribution : catalog.distributions(settings.catalogMaxAgeHours(), forceRefresh).value()) {
            if (distribution.buildOfGraalvm() && !settings.showGraalvm()) {
                continue;
            }
            vendors.add(distribution);
        }
        return vendors;
    }

    public Map<Integer, String> majors(Distribution distribution, Settings settings) {
        Map<Integer, String> majors = new LinkedHashMap<>();
        for (String version : distribution.versions()) {
            boolean earlyAccess = version.contains("-ea");
            if (earlyAccess && !settings.showEarlyAccess()) {
                continue;
            }
            int major = MajorVersion.fromJavaVersion(version.startsWith("1.") ? version : version.split("-")[0]);
            if (major < 8 && settings.hideMajorsBelow8()) {
                continue;
            }
            majors.putIfAbsent(major, version);
        }
        return majors;
    }

    public Card latest(Distribution distribution, int major, Settings settings, boolean javafx, boolean forceRefresh)
            throws IOException {
        DiscoCatalog.Load<List<CatalogPackage>> load = catalog.packages(
                distribution.apiParameter(),
                major,
                javafx,
                true,
                settings.showGraalvm(),
                settings.catalogMaxAgeHours(),
                forceRefresh);
        CatalogPackage pkg = load.value().isEmpty() ? null : load.value().get(0);
        return new Card(
                distribution.name(),
                distribution.apiParameter(),
                major,
                pkg,
                pkg != null && installed(pkg, settings),
                load.stale());
    }

    public List<CatalogPackage> versions(Distribution distribution, int major, Settings settings, boolean javafx)
            throws IOException {
        return catalog.packages(
                distribution.apiParameter(),
                major,
                javafx,
                false,
                settings.showGraalvm(),
                settings.catalogMaxAgeHours(),
                true).value();
    }

    public static boolean installed(CatalogPackage pkg, Settings settings) {
        Path destination = Layout.destination(
                Path.of(settings.rootDirectory()),
                Slug.of(pkg.distribution(), pkg.javafxBundled(), pkg.architecture()),
                pkg.major());
        return Files.isDirectory(destination);
    }

    public static boolean matches(
            Card card,
            String vendorText,
            Integer major,
            boolean ltsOnly,
            boolean installedOnly,
            boolean updateAvailable,
            boolean javafx,
            List<Installation> installed) {
        if (card.pkg() == null) {
            return false;
        }
        if (vendorText != null && !vendorText.isBlank()
                && !card.vendor().toLowerCase().contains(vendorText.toLowerCase())
                && !card.apiParameter().toLowerCase().contains(vendorText.toLowerCase())) {
            return false;
        }
        if (major != null && card.major() != major) {
            return false;
        }
        if (ltsOnly && !"lts".equalsIgnoreCase(card.pkg().termOfSupport())) {
            return false;
        }
        if (javafx && !card.pkg().javafxBundled()) {
            return false;
        }
        if (installedOnly && !card.installed()) {
            return false;
        }
        if (updateAvailable && !updateAvailable(card, installed)) {
            return false;
        }
        return true;
    }

    public static boolean updateAvailable(Card card, List<Installation> installed) {
        if (card.pkg() == null) {
            return false;
        }
        for (Installation installation : installed) {
            if (installation.discoPackageId().equals(card.pkg().id())
                    && !installation.javaVersion().equals(card.pkg().javaVersion())) {
                return true;
            }
        }
        return false;
    }
}
