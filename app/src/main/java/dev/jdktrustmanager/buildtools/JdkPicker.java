package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.catalog.MajorVersion;
import dev.jdktrustmanager.model.Installation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Chooses the JDK to run a tool with: the active one if it's in range, else the newest managed one that is. */
public final class JdkPicker {

    private JdkPicker() {
    }

    public static Optional<Path> pick(JavaRange range, String activeJavaHome, List<Installation> managed) {
        if (activeJavaHome != null && !activeJavaHome.isBlank()) {
            Path active = Path.of(activeJavaHome);
            Optional<Integer> major = major(active);
            if (major.isPresent() && range.allows(major.get()) && Files.isRegularFile(active.resolve("bin").resolve("java.exe"))) {
                return Optional.of(active);
            }
        }
        return managed.stream()
                .filter(installation -> range.allows(installation.major()))
                .filter(installation -> Files.isRegularFile(Path.of(installation.path()).resolve("bin").resolve("java.exe")))
                .max(Comparator.comparingInt(Installation::major))
                .map(installation -> Path.of(installation.path()));
    }

    /** The feature version from a JDK home's {@code release} file. */
    public static Optional<Integer> major(Path home) {
        try {
            for (String line : Files.readAllLines(home.resolve("release"))) {
                if (line.startsWith("JAVA_VERSION=")) {
                    return Optional.of(MajorVersion.fromJavaVersion(line.substring("JAVA_VERSION=".length()).replace("\"", "").trim()));
                }
            }
        } catch (Exception ignored) {
            // Not a readable JDK home.
        }
        return Optional.empty();
    }
}
