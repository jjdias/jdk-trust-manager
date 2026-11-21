package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.catalog.PackageDetail;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Installation;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipInstallerTest {

    private static final String TEMURIN_SHA = "a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9";

    @TempDir
    Path directory;

    @Test
    void rejectsParentEntries() throws Exception {
        Path zip = directory.resolve("bad.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("../evil.txt"));
            output.write("no".getBytes());
            output.closeEntry();
        }
        IOException failure = assertThrows(IOException.class, () -> ZipInstaller.unzip(zip, directory.resolve("out")));
        assertTrue(failure.getMessage().contains("Rejected zip entry"));
    }

    @Test
    void installsCachedTemurinAndRollsBackAMismatchedRelease() throws Exception {
        Path cached = dev.jdktrustmanager.persist.AppFiles.zipCache().resolve(TEMURIN_SHA + ".zip");
        Assumptions.assumeTrue(Files.isRegularFile(cached),
                "Skipped: install Temurin 21.0.13+9 once with the app so its zip is in the local cache");
        Path cache = directory.resolve("cache");
        Files.createDirectories(cache);
        Files.copy(cached, cache.resolve(TEMURIN_SHA + ".zip"));
        CatalogPackage pkg = new CatalogPackage(
                "temurin-21",
                "temurin",
                21,
                "21.0.13+9",
                "zip",
                "x64",
                false,
                true,
                true,
                "ga",
                "lts",
                "unknown",
                "OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip",
                Files.size(cached),
                false);
        PackageDetail detail = new PackageDetail("https://example.invalid/temurin.zip", TEMURIN_SHA, "sha256");
        ZipInstaller installer = new ZipInstaller();
        Installation installed = installer.install(pkg, detail, directory, cache, null, ZipInstaller.TrustReplay.noop());
        assertEquals(21, installed.major());
        assertTrue(Files.isRegularFile(directory.resolve("temurin-21").resolve("bin").resolve("java.exe")));

        Path altered = directory.resolve("altered.zip");
        rewriteRelease(directory.resolve("temurin-21"), altered, "17.0.1");
        String alteredSha = ZipInstaller.sha256(altered);
        Files.copy(altered, cache.resolve(alteredSha + ".zip"));
        CatalogPackage wrong = new CatalogPackage(
                "temurin-21",
                "temurin",
                21,
                "17.0.1",
                "zip",
                "x64",
                false,
                true,
                true,
                "ga",
                "lts",
                "unknown",
                "altered.zip",
                Files.size(altered),
                false);
        PackageDetail wrongDetail = new PackageDetail("https://example.invalid/altered.zip", alteredSha, "sha256");
        assertThrows(IOException.class, () -> installer.install(
                wrong, wrongDetail, directory.resolve("other-root"), cache, null, ZipInstaller.TrustReplay.noop()));
        assertFalse(Files.exists(directory.resolve("other-root").resolve("temurin-21")));
        assertTrue(Files.isRegularFile(cache.resolve(alteredSha + ".zip")));
    }

    private static void rewriteRelease(Path jdkHome, Path zip, String version) throws Exception {
        Path release = jdkHome.resolve("release");
        String original = Files.readString(release);
        Files.writeString(release, original.replaceAll("JAVA_VERSION=\"[^\"]*\"", "JAVA_VERSION=\"" + version + "\""));
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip)); var walk = Files.walk(jdkHome)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String name = "jdk/" + jdkHome.relativize(path).toString().replace('\\', '/');
                output.putNextEntry(new ZipEntry(name));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        Files.writeString(release, original);
    }

    @Test
    void downloadFollowsARedirect() throws Exception {
        byte[] body = "jdk-zip".getBytes();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        server.createContext("/file", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/payload");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/payload", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        try {
            Path target = directory.resolve("redirected.zip");
            new ZipInstaller().download("http://127.0.0.1:" + port + "/file", target, body.length, null);
            assertArrayEquals(body, Files.readAllBytes(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellingADownloadStopsItAndDeletesThePartialFile() throws Exception {
        byte[] body = new byte[256 * 1024];
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            } catch (java.io.IOException ignored) {
                // The client hangs up when it cancels.
            }
            exchange.close();
        });
        server.start();
        java.util.concurrent.atomic.AtomicLong seen = new java.util.concurrent.atomic.AtomicLong();
        ZipInstaller.InstallProgress progress = new ZipInstaller.InstallProgress() {
            @Override
            public void log(String line) {
            }

            @Override
            public void bytes(long read, long total) {
                seen.set(read);
            }

            @Override
            public void phase(String phase) {
            }

            @Override
            public boolean cancelled() {
                return seen.get() > 0;
            }
        };
        try {
            Path target = directory.resolve("cancelled.zip");
            org.junit.jupiter.api.Assertions.assertThrows(ZipInstaller.CancelledException.class, () -> new ZipInstaller()
                    .download("http://127.0.0.1:" + server.getAddress().getPort() + "/big", target, body.length, progress));
            org.junit.jupiter.api.Assertions.assertFalse(Files.exists(target));
            org.junit.jupiter.api.Assertions.assertTrue(seen.get() < body.length);
        } finally {
            server.stop(0);
        }
    }
}
