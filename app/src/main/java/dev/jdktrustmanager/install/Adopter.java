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
                release.getOrDefault("OS_ARCH", "x64"),
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
