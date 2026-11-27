package dev.jdktrustmanager.install;

import dev.jdktrustmanager.catalog.MajorVersion;
import dev.jdktrustmanager.catalog.PackageDetail;
import dev.jdktrustmanager.catalog.Slug;
import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipInstaller {

    public interface InstallProgress {
        void log(String line);

        void bytes(long read, long total);

        void phase(String phase);

        /** Checked between download chunks. Returning true stops the download and deletes the partial file. */
        default boolean cancelled() {
            return false;
        }
    }

    /** Thrown when the user cancels a download. Nothing was installed and the partial zip is gone. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("Download cancelled");
        }
    }

    @FunctionalInterface
    public interface TrustReplay {
        void replay(Path jdkHome) throws IOException;

        static TrustReplay noop() {
            return home -> {
            };
        }
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Installation install(
            dev.jdktrustmanager.model.CatalogPackage pkg,
            PackageDetail detail,
            Path root,
            Path zipCacheDirectory,
            Path bundledRuntime,
            TrustReplay replay) throws IOException {
        return install(pkg, detail, root, zipCacheDirectory, bundledRuntime, replay, null);
    }

    public Installation install(
            dev.jdktrustmanager.model.CatalogPackage pkg,
            PackageDetail detail,
            Path root,
            Path zipCacheDirectory,
            Path bundledRuntime,
            TrustReplay replay,
            InstallProgress progress) throws IOException {
        if (detail == null || detail.checksum() == null || detail.checksum().isBlank() || !"sha256".equals(detail.checksumType())) {
            throw new IOException("SHA-256 is required");
        }
        Files.createDirectories(root);
        if (pkg.size() > 0 && Files.getFileStore(root).getUsableSpace() < pkg.size()) {
            throw new IOException("Not enough free space for " + pkg.filename());
        }
        Files.createDirectories(zipCacheDirectory);
        Path zip = zipCacheDirectory.resolve(detail.checksum() + ".zip");
        note(progress, "Package " + pkg.id() + " " + pkg.javaVersion() + " (" + pkg.size() + " bytes)");
        note(progress, "GET " + detail.directDownloadUri());
        note(progress, "Expected SHA-256 " + detail.checksum());
        if (!Files.isRegularFile(zip) || !detail.checksum().equalsIgnoreCase(sha256(zip))) {
            download(detail.directDownloadUri(), zip, pkg.size(), progress);
            phase(progress, "Verifying SHA-256");
            String actual = sha256(zip);
            note(progress, "Actual SHA-256 " + actual);
            if (!detail.checksum().equalsIgnoreCase(actual)) {
                Files.deleteIfExists(zip);
                throw new IOException("Checksum mismatch");
            }
        } else {
            note(progress, "Using cached zip " + zip);
        }
        String slug = Slug.of(pkg.distribution(), pkg.javafxBundled(), pkg.architecture());
        Path destination = Layout.destination(root, slug, pkg.major());
        Layout.checkInsideRoot(root, destination, bundledRuntime);
        Path backup = null;
        if (Files.exists(destination)) {
            backup = destination.resolveSibling(destination.getFileName() + ".bak-" + Instant.now().toEpochMilli());
            Files.move(destination, backup);
        }
        Path unpacked = Files.createTempDirectory(root, ".unpack-");
        try {
            phase(progress, "Unpacking " + zip.getFileName());
            unzip(zip, unpacked);
            Path home = findHome(unpacked);
            note(progress, "JDK home inside zip: " + home.getFileName());
            phase(progress, "Moving to " + destination);
            moveWithRetry(home, destination);
            String version = verifyJavaRuns(destination.resolve("bin").resolve("java.exe"));
            note(progress, version);
            Map<String, String> release = readRelease(destination.resolve("release"));
            int feature = MajorVersion.fromJavaVersion(release.getOrDefault("JAVA_VERSION", ""));
            if (feature != pkg.major()) {
                throw new IOException("Feature version " + feature + " does not match " + pkg.major());
            }
            replay.replay(destination);
            if (backup != null) {
                deleteRecursively(backup);
            }
            return new Installation(
                    slug,
                    pkg.major(),
                    pkg.id(),
                    release.get("JAVA_VERSION"),
                    pkg.javafxBundled(),
                    destination.toString(),
                    pkg.architecture(),
                    release.getOrDefault("IMPLEMENTOR", ""),
                    release.get("JAVA_VERSION"),
                    detail.checksum(),
                    Instant.now(),
                    InstallSource.FOOJAY,
                    false,
                    null,
                    null,
                    false,
                    "");
        } catch (IOException ex) {
            deleteRecursively(destination);
            if (backup != null && Files.exists(backup)) {
                Files.move(backup, destination);
            }
            throw ex;
        } finally {
            deleteRecursively(unpacked);
        }
    }

    public static void unzip(Path zip, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..") || (name.length() >= 2 && name.charAt(1) == ':')) {
                    throw new IOException("Rejected zip entry " + entry.getName());
                }
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Rejected zip entry " + entry.getName());
                }
                if (entry.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target);
                }
                input.closeEntry();
            }
        }
    }

    public void download(String uri, Path target, long expectedSize, InstallProgress progress) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .header("User-Agent", "JdkTrustManager")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            response.previousResponse().ifPresent(previous -> note(
                    progress,
                    "Redirect " + previous.statusCode() + " " + previous.headers().firstValue("Location").orElse(previous.uri().toString())));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Download failed: " + response.statusCode());
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(expectedSize);
            note(progress, "HTTP " + response.statusCode() + ", content length " + total);
            long read = 0;
            long nextLog = Math.max(total / 10, 1);
            byte[] buffer = new byte[8192];
            boolean cancelled = false;
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
                int n;
                while ((n = input.read(buffer)) >= 0) {
                    if (progress != null && progress.cancelled()) {
                        cancelled = true;
                        break;
                    }
                    output.write(buffer, 0, n);
                    read += n;
                    if (progress != null) {
                        progress.bytes(read, total);
                    }
                    if (read >= nextLog) {
                        note(progress, "Downloaded " + read + " of " + total);
                        nextLog += Math.max(total / 10, 1);
                    }
                }
            }
            if (cancelled) {
                Files.deleteIfExists(target);
                throw new CancelledException();
            }
            note(progress, "Saved " + target + " (" + read + " bytes)");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
    }

    private static void note(InstallProgress progress, String line) {
        if (progress != null && line != null && !line.isBlank()) {
            progress.log(line);
        }
    }

    private static void phase(InstallProgress progress, String phase) {
        if (progress != null) {
            progress.phase(phase);
        }
    }

    private static Path findHome(Path unpacked) throws IOException {
        List<Path> homes = new ArrayList<>();
        try (var walk = Files.walk(unpacked)) {
            walk.filter(path -> "release".equals(path.getFileName().toString()) && Files.isRegularFile(path))
                    .map(Path::getParent)
                    .filter(dir -> Files.isRegularFile(dir.resolve("bin").resolve("java.exe")))
                    .forEach(homes::add);
        }
        if (homes.size() != 1) {
            throw new IOException("Expected one JDK home, found " + homes.size());
        }
        return homes.get(0);
    }

    private static void moveWithRetry(Path home, Path destination) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.move(home, destination);
                return;
            } catch (IOException ex) {
                last = ex;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
        }
        throw last;
    }

    private static String verifyJavaRuns(Path javaExe) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(javaExe.toString(), "-version");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0) {
                throw new IOException("java -version failed: " + output);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
    }

    static Map<String, String> readRelease(Path release) throws IOException {
        Map<String, String> values = new java.util.HashMap<>();
        for (String line : Files.readAllLines(release)) {
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String value = line.substring(equals + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(line.substring(0, equals).trim(), value);
        }
        return values;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                input.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
