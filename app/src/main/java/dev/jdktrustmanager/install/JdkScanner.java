package dev.jdktrustmanager.install;

import dev.jdktrustmanager.model.Installation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JdkScanner {

    public static final List<Path> WELL_KNOWN = List.of(
            Path.of("C:\\Program Files\\Eclipse Adoptium"),
            Path.of("C:\\Program Files\\Zulu"),
            Path.of("C:\\Program Files\\Microsoft"),
            Path.of("C:\\Program Files\\Amazon Corretto"),
            Path.of("C:\\Program Files\\BellSoft"),
            Path.of("C:\\Program Files\\SapMachine"),
            Path.of("C:\\Program Files\\OpenLogic"));

    public record Discovered(Path path, boolean managed) {
    }

    public List<Discovered> scan(Path root, List<Installation> known, List<String> registryLines) throws IOException {
        return scan(root, WELL_KNOWN, known, registryLines);
    }

    public List<Discovered> scan(Path root, List<Path> extraRoots, List<Installation> known, List<String> registryLines)
            throws IOException {
        List<Path> homes = new ArrayList<>();
        collect(root, homes, 0);
        for (Path wellKnown : extraRoots) {
            collect(wellKnown, homes, 0);
        }
        for (String home : JavaSoftHomes.parse(registryLines)) {
            Path path = Path.of(home);
            if (isHome(path)) {
                homes.add(path);
            }
        }
        List<Discovered> found = new ArrayList<>();
        for (Path home : homes) {
            boolean managed = known.stream().anyMatch(installation -> Path.of(installation.path()).toAbsolutePath().normalize().equals(home.toAbsolutePath().normalize()));
            found.add(new Discovered(home, managed));
        }
        return found;
    }

    private static void collect(Path directory, List<Path> homes, int depth) throws IOException {
        if (directory == null || depth > 2 || Files.notExists(directory) || !Files.isDirectory(directory)) {
            return;
        }
        if (directory.getFileName() != null && (Layout.TRUSTED_CERTS.equals(directory.getFileName().toString())
                || Layout.BUILD_TOOLS.equals(directory.getFileName().toString()))) {
            return;
        }
        if (isHome(directory)) {
            homes.add(directory);
            return;
        }
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                collect(child, homes, depth + 1);
            }
        } catch (IOException ignored) {
            return;
        }
    }

    public static boolean isHome(Path path) {
        return Files.isRegularFile(path.resolve("release")) && Files.isRegularFile(path.resolve("bin").resolve("java.exe"));
    }
}
