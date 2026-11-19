package dev.jdktrustmanager.env;

import java.util.Locale;

public final class PathUsage {

    private PathUsage() {
    }

    public static boolean uses(String home, String javaHome, String path) {
        String normalized = normalize(home);
        if (normalized.isEmpty()) {
            return false;
        }
        if (same(normalized, javaHome)) {
            return true;
        }
        return referenced(normalized, javaHome, path);
    }

    public static boolean uses(String home, String userJavaHome, String userPath, String machineJavaHome, String machinePath) {
        String normalized = normalize(home);
        if (uses(normalized, userJavaHome, userPath) || same(normalized, machineJavaHome)) {
            return true;
        }
        String expand = machineJavaHome == null || machineJavaHome.isBlank() ? userJavaHome : machineJavaHome;
        return referenced(normalized, expand, machinePath);
    }

    private static boolean referenced(String home, String javaHome, String path) {
        if (home.isEmpty() || path == null || path.isBlank()) {
            return false;
        }
        for (String entry : path.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            if (pointsAt(home, entry) || pointsAt(home, expand(entry, javaHome))) {
                return true;
            }
        }
        return false;
    }

    private static String expand(String entry, String javaHome) {
        if (javaHome == null || javaHome.isBlank()) {
            return entry;
        }
        return entry.replaceAll("(?i)%JAVA_HOME%", javaHome.replace("\\", "\\\\"));
    }

    private static boolean pointsAt(String home, String entry) {
        String value = normalize(entry);
        String bin = home + "\\bin";
        return value.equals(home) || value.equals(bin) || value.startsWith(bin + "\\");
    }

    private static boolean same(String home, String javaHome) {
        return normalize(javaHome).equals(home);
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String value = path.trim().replace('/', '\\');
        while (value.endsWith("\\")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
