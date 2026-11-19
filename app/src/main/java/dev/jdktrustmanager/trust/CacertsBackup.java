package dev.jdktrustmanager.trust;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class CacertsBackup {

    public Path backup(Path home) throws IOException {
        Path cacerts = home.resolve("lib").resolve("security").resolve("cacerts");
        Path destination = home.resolve(".jdk-trust-manager").resolve("backups").resolve(Long.toString(Instant.now().toEpochMilli())).resolve("cacerts");
        Files.createDirectories(destination.getParent());
        Files.copy(cacerts, destination);
        trim(home.resolve(".jdk-trust-manager").resolve("backups"));
        return destination;
    }

    public void restore(Path backup, Path cacerts) throws IOException {
        Files.copy(backup, cacerts, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void trim(Path backups) throws IOException {
        try (var children = Files.list(backups)) {
            List<Path> stamps = children.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            int extra = stamps.size() - 10;
            for (int index = 0; index < extra; index++) {
                Files.deleteIfExists(stamps.get(index).resolve("cacerts"));
                Files.deleteIfExists(stamps.get(index));
            }
        }
    }
}
