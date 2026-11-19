package dev.jdktrustmanager.env;

import java.util.ArrayList;
import java.util.List;

public final class PathEditor {

    public static final String MANAGED = "%JAVA_HOME%\\bin";

    private PathEditor() {
    }

    public static String edit(String current, String root, boolean removeOtherJava) {
        List<String> next = new ArrayList<>();
        next.add(MANAGED);
        for (String entry : split(current)) {
            if (isManaged(entry, root)) {
                continue;
            }
            if (removeOtherJava && isOtherJava(entry)) {
                continue;
            }
            next.add(entry);
        }
        return String.join(";", next);
    }

    public static boolean isManaged(String entry, String root) {
        String normalized = entry.trim().replace('/', '\\');
        if (normalized.equalsIgnoreCase(MANAGED)) {
            return true;
        }
        String prefix = root.replace('/', '\\');
        if (!prefix.endsWith("\\")) {
            prefix = prefix + "\\";
        }
        return normalized.toLowerCase().startsWith(prefix.toLowerCase()) && normalized.toLowerCase().endsWith("\\bin");
    }

    public static boolean isOtherJava(String entry) {
        String lower = entry.toLowerCase();
        return lower.contains("\\java\\") || lower.contains("javapath") || lower.contains("jdk");
    }

    private static List<String> split(String current) {
        List<String> entries = new ArrayList<>();
        if (current == null || current.isBlank()) {
            return entries;
        }
        for (String entry : current.split(";")) {
            if (!entry.isBlank()) {
                entries.add(entry.trim());
            }
        }
        return entries;
    }
}
