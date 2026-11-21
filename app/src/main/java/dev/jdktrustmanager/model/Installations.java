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

    /** Sets or clears the pin on the installation at {@code path}. Pinned JDKs are never removed or updated. */
    public static List<Installation> withPinned(List<Installation> known, String path, boolean pinned) {
        List<Installation> next = new ArrayList<>(known.size());
        for (Installation item : known) {
            next.add(samePath(item.path(), path) && item.pinned() != pinned
                    ? new Installation(item.vendorSlug(), item.major(), item.discoPackageId(), item.javaVersion(),
                            item.javafxBundled(), item.path(), item.architecture(), item.implementor(),
                            item.releaseVersion(), item.zipSha256(), item.installedAt(), item.source(), item.active(),
                            item.lastTrustSync(), item.overlayFingerprint(), pinned, item.notes())
                    : item);
        }
        return next;
    }

    /** A reinstall or update produces a fresh record; keep what the user set on the old one (active, pinned, notes). */
    public static Installation keepUserFlags(Installation previous, Installation fresh) {
        if (previous == null) {
            return fresh;
        }
        return new Installation(fresh.vendorSlug(), fresh.major(), fresh.discoPackageId(), fresh.javaVersion(),
                fresh.javafxBundled(), fresh.path(), fresh.architecture(), fresh.implementor(), fresh.releaseVersion(),
                fresh.zipSha256(), fresh.installedAt(), fresh.source(), previous.active(), fresh.lastTrustSync(),
                fresh.overlayFingerprint(), previous.pinned(), previous.notes());
    }

    /**
     * True when {@code candidate} is a later release than {@code current}. Build numbers after "+" are ignored, so
     * "21.0.5+11" from the catalog equals "21.0.5" from a JDK's release file. "1.8.0_432" is read as 8.0.432.
     */
    public static boolean newer(String candidate, String current) {
        int[] left = numbers(candidate);
        int[] right = numbers(current);
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int a = index < left.length ? left[index] : 0;
            int b = index < right.length ? right[index] : 0;
            if (a != b) {
                return a > b;
            }
        }
        return false;
    }

    private static int[] numbers(String version) {
        if (version == null) {
            return new int[0];
        }
        String text = version.trim();
        int cut = indexOfAny(text, '+', '-', ' ');
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        if (text.startsWith("1.")) {
            text = text.substring(2);
        }
        String[] parts = text.replace('_', '.').split("\\.");
        int[] values = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String digits = parts[index].replaceAll("\\D", "");
            values[index] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return values;
    }

    private static int indexOfAny(String text, char... characters) {
        int best = -1;
        for (char character : characters) {
            int index = text.indexOf(character);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
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
