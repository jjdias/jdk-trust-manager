package dev.jdktrustmanager.catalog;

public final class MajorVersion {

    private MajorVersion() {
    }

    public static int fromJavaVersion(String javaVersion) {
        if (javaVersion.startsWith("1.")) {
            int secondDot = javaVersion.indexOf('.', 2);
            String feature = secondDot < 0 ? javaVersion.substring(2) : javaVersion.substring(2, secondDot);
            return Integer.parseInt(feature);
        }
        int end = 0;
        while (end < javaVersion.length() && Character.isDigit(javaVersion.charAt(end))) {
            end++;
        }
        if (end == 0) {
            throw new IllegalArgumentException("No major version in " + javaVersion);
        }
        return Integer.parseInt(javaVersion.substring(0, end));
    }
}
