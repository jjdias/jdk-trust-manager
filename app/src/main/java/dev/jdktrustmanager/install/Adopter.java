package dev.jdktrustmanager.install;

import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

public final class Adopter {

    /** Reads {@code source}'s release file and works out where it would be adopted to. */
    public AdoptPlan plan(Path source, Path root) throws IOException {
        if (!JdkScanner.isHome(source)) {
            throw new IOException("Not a JDK home: " + source);
        }
        return AdoptPlan.of(source, root, ZipInstaller.readRelease(source.resolve("release")));
    }

    /**
     * Brings a JDK under management. In place when it already sits at its managed folder; otherwise it is copied
     * there and the original stays where it was. Never merges into an existing folder.
     */
    public Installation adopt(AdoptPlan plan, Path root, Path bundledRuntime) throws IOException {
        Path destination = plan.destination();
        Layout.checkInsideRoot(root, destination, bundledRuntime);
        if (!plan.inPlace()) {
            if (Files.exists(destination)) {
                throw new IOException(destination + " already exists. Remove it or adopt that folder instead.");
            }
            Files.createDirectories(destination.getParent());
            Path partial = destination.resolveSibling(destination.getFileName() + ".adopt-" + Instant.now().toEpochMilli());
            try {
                copy(plan.source(), partial);
                Files.move(partial, destination);
            } catch (IOException ex) {
                ZipInstaller.deleteRecursively(partial);
                throw ex;
            }
        }
        Map<String, String> release = ZipInstaller.readRelease(destination.resolve("release"));
        return new Installation(
                plan.slug(),
                plan.major(),
                "",
                release.getOrDefault("JAVA_VERSION", plan.javaVersion()),
                plan.javafx(),
                destination.toString(),
                plan.architecture(),
                release.getOrDefault("IMPLEMENTOR", plan.implementor()),
                release.getOrDefault("JAVA_VERSION", plan.javaVersion()),
                "",
                Instant.now(),
                InstallSource.ADOPTED,
                false,
                null,
                null,
                false,
                "");
    }

    public Installation adopt(Path source, Path root, String slug, int major, boolean move) throws IOException {
        if (!JdkScanner.isHome(source)) {
            throw new IOException("Not a JDK home: " + source);
        }
        Path destination = Layout.destination(root, slug, major);
        Layout.checkInsideRoot(root, destination, null);
        Files.createDirectories(destination.getParent());
        if (move) {
            Files.move(source, destination);
        } else {
            copy(source, destination);
        }
        Map<String, String> release = ZipInstaller.readRelease(destination.resolve("release"));
        return new Installation(
                slug,
                major,
                "",
                release.getOrDefault("JAVA_VERSION", ""),
                slug.endsWith("-fx"),
                destination.toString(),
                AdoptPlan.architecture(release.getOrDefault("OS_ARCH", "")),
                release.getOrDefault("IMPLEMENTOR", ""),
                release.getOrDefault("JAVA_VERSION", ""),
                "",
                Instant.now(),
                InstallSource.ADOPTED,
                false,
                null,
                null,
                false,
                "");
    }

    private static void copy(Path source, Path destination) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
