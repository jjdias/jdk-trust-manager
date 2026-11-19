package dev.jdktrustmanager.env;

import dev.jdktrustmanager.catalog.MajorVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JavaOnPath {

    public record Jdk(Path home, int major) {
    }

    private JavaOnPath() {
    }

    public static String effectivePath(String machinePath, String userPath) {
        boolean machine = machinePath != null && !machinePath.isBlank();
        boolean user = userPath != null && !userPath.isBlank();
        if (machine && user) {
            return machinePath + ";" + userPath;
        }
        if (machine) {
            return machinePath;
        }
        return user ? userPath : "";
    }

    public static Path findJava(String machinePath, String userPath, Map<String, String> variables) {
        String expanded = expand(effectivePath(machinePath, userPath), variables);
        for (String entry : expanded.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            Path exe = Path.of(entry.trim()).resolve("java.exe");
            if (Files.isRegularFile(exe)) {
                return exe;
            }
        }
        return null;
    }

    public static Jdk jdk(Path javaExecutable) throws IOException {
        Path bin = javaExecutable.toAbsolutePath().normalize().getParent();
        if (bin == null || bin.getFileName() == null || !bin.getFileName().toString().equalsIgnoreCase("bin")) {
            throw new IOException("java on PATH is not inside a JDK bin directory: " + javaExecutable);
        }
        Path home = bin.getParent();
        if (home == null || !Files.isRegularFile(home.resolve("lib").resolve("security").resolve("cacerts"))) {
            throw new IOException("java on PATH has no cacerts: " + javaExecutable);
        }
        String version = releaseValue(home.resolve("release"), "JAVA_VERSION");
        if (version.isBlank()) {
            throw new IOException("java on PATH has no JAVA_VERSION: " + home);
        }
        return new Jdk(home, MajorVersion.fromJavaVersion(version));
    }

    public static String expand(String text, Map<String, String> variables) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String current = text;
        for (int pass = 0; pass < 5; pass++) {
            String next = replaceOnce(current, variables);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        return current;
    }

    private static String replaceOnce(String text, Map<String, String> variables) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf('%', index);
            if (start < 0 || start == text.length() - 1) {
                out.append(text.substring(index));
                break;
            }
            int end = text.indexOf('%', start + 1);
            if (end < 0) {
                out.append(text.substring(index));
                break;
            }
            String value = find(variables, text.substring(start + 1, end));
            if (value == null) {
                out.append(text, index, end + 1);
                index = end + 1;
            } else {
                out.append(text, index, start);
                out.append(value);
                index = end + 1;
            }
        }
        return out.toString();
    }

    private static String find(Map<String, String> variables, String name) {
        if (variables == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String releaseValue(Path release, String key) throws IOException {
        if (!Files.isRegularFile(release)) {
            return "";
        }
        for (String line : Files.readAllLines(release)) {
            int equals = line.indexOf('=');
            if (equals < 0 || !line.substring(0, equals).trim().equals(key)) {
                continue;
            }
            String value = line.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
        return "";
    }
}
