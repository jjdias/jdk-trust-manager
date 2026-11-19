package dev.jdktrustmanager.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Installations {

    private Installations() {
    }

    public static List<Installation> markActive(List<Installation> known, String activePath) {
        List<Installation> next = new ArrayList<>(known.size());
        for (Installation item : known) {
            boolean active = samePath(item.path(), activePath);
            next.add(item.active() == active ? item : withActive(item, active));
        }
        return next;
    }

    public static boolean samePath(String left, String right) {
        return left != null && right != null && key(left).equals(key(right));
    }

    private static String key(String path) {
        String text = path.trim();
        while (text.length() > 3 && (text.endsWith("\\") || text.endsWith("/"))) {
            text = text.substring(0, text.length() - 1);
        }
        return text.replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private static Installation withActive(Installation item, boolean active) {
        return new Installation(item.vendorSlug(), item.major(), item.discoPackageId(), item.javaVersion(),
                item.javafxBundled(), item.path(), item.architecture(), item.implementor(), item.releaseVersion(),
                item.zipSha256(), item.installedAt(), item.source(), active, item.lastTrustSync(),
                item.overlayFingerprint(), item.pinned(), item.notes());
    }
}
