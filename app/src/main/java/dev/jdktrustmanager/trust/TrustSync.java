package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateChoice;
import dev.jdktrustmanager.model.SourcesDocument;
import dev.jdktrustmanager.model.UrlEntry;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class TrustSync {

    public static final String MOZILLA = "https://curl.se/ca/cacert.pem";
    public static final String TESTNG = "https://testng.org";

    public Path ensureLayout(Path root, JsonStores stores) throws IOException {
        Path trusted = root.resolve("trusted-certs");
        Files.createDirectories(trusted.resolve("urls"));
        Files.createDirectories(trusted.resolve("local"));
        Path sources = trusted.resolve("sources.json");
        new UrlCatalog().load(sources);
        return trusted;
    }

    public List<String> certificatesIn(String pem) {
        if (pem.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("Refusing a private key");
        }
        List<String> certificates = new ArrayList<>();
        int from = 0;
        while (from < pem.length()) {
            int begin = pem.indexOf("-----BEGIN CERTIFICATE-----", from);
            if (begin < 0) {
                break;
            }
            int end = pem.indexOf("-----END CERTIFICATE-----", begin);
            if (end < 0) {
                throw new IllegalArgumentException("PEM has no certificate");
            }
            certificates.add(pem.substring(begin, end + "-----END CERTIFICATE-----".length()));
            from = end + 1;
        }
        return certificates;
    }

    public void syncHomes(Path root, List<Path> homes, Path bundledRuntime, Consumer<String> log) throws IOException, InterruptedException {
        Path trusted = ensureLayout(root, new JsonStores());
        Consumer<String> lines = log == null ? message -> { } : log;
        List<String> checked = new ArrayList<>();
        TrustDownloads downloads = new TrustDownloads();
        for (UrlEntry entry : UrlCatalog.enabledOnly(new UrlCatalog().load(trusted.resolve("sources.json")))) {
            lines.accept("Fetching " + entry.url());
            String previous = fingerprintsOf(folderFor(trusted, entry.url()));
            List<String> fetched = downloads.certificatesFor(entry.url());
            writeFetched(trusted, entry.url(), fetched);
            lines.accept(entry.url() + " fingerprints " + previous + " -> " + fingerprintsOf(folderFor(trusted, entry.url())));
            checked.addAll(fetched);
        }
        for (Path dropped : refreshLocal(trusted)) {
            String text = Files.readString(dropped);
            if (text.contains("PRIVATE KEY")) {
                throw new IllegalArgumentException("Refusing a private key in " + dropped.getFileName());
            }
            checked.addAll(certificatesIn(text));
        }
        for (Path home : homes) {
            if (home == null || Files.notExists(home)) {
                continue;
            }
            if (bundledRuntime != null && home.toAbsolutePath().normalize().startsWith(bundledRuntime.toAbsolutePath().normalize())) {
                lines.accept("Skipping bundled runtime " + home);
                continue;
            }
            syncOne(home, unique(checked), lines);
        }
        Files.writeString(trusted.resolve("last-sync"), java.time.Instant.now().toString());
    }

    public List<String> unique(List<String> certificates) throws IOException {
        java.util.LinkedHashMap<String, String> once = new java.util.LinkedHashMap<>();
        for (String pem : certificates) {
            once.putIfAbsent(sha256(certificate(pem, null)), pem);
        }
        return new ArrayList<>(once.values());
    }

    private static void writeFetched(Path trusted, String url, List<String> certificates) throws IOException {
        Path folder = folderFor(trusted, url);
        if (Files.isDirectory(folder)) {
            try (var children = Files.list(folder)) {
                for (Path child : children.toList()) {
                    Files.deleteIfExists(child);
                }
            }
        }
        Files.createDirectories(folder);
        for (int index = 0; index < certificates.size(); index++) {
            Files.writeString(folder.resolve((index + 1) + ".pem"), certificates.get(index));
        }
    }

    private static Path folderFor(Path trusted, String url) {
        String host = URI.create(url).getHost().replaceAll("[^A-Za-z0-9.-]", "_");
        return trusted.resolve("urls").resolve(host);
    }

    private void syncOne(Path home, List<String> checked, Consumer<String> lines) throws IOException, InterruptedException {
        Path keytool = home.resolve("bin").resolve("keytool.exe");
        Path cacerts = home.resolve("lib").resolve("security").resolve("cacerts");
        if (!Files.isRegularFile(keytool) || !Files.isRegularFile(cacerts)) {
            lines.accept("No cacerts in " + home);
            return;
        }
        Set<String> present = new LinkedHashSet<>();
        int vendorAliases = 0;
        for (var entry : new KeytoolStore().listCacerts(home, 11)) {
            if (entry.alias() != null && !entry.alias().startsWith("jkm-")) {
                vendorAliases++;
            }
            if (entry.sha256() != null) {
                present.add(entry.sha256().toLowerCase());
            }
        }
        boolean backedUp = false;
        int added = 0;
        for (String pem : checked) {
            byte[] der = certificate(pem, null);
            String fingerprint = sha256(der);
            if (present.contains(fingerprint.toLowerCase())) {
                continue;
            }
            if (!backedUp) {
                new CacertsBackup().backup(home);
                backedUp = true;
            }
            Path file = Files.createTempFile("jkm-", ".pem");
            try {
                Files.writeString(file, pem.endsWith("\n") ? pem : pem + "\n");
                importOnce(keytool, cacerts, file, alias(der));
            } finally {
                Files.deleteIfExists(file);
            }
            present.add(fingerprint.toLowerCase());
            added++;
        }
        int vendorAfter = 0;
        for (var entry : new KeytoolStore().listCacerts(home, 11)) {
            if (entry.alias() != null && !entry.alias().startsWith("jkm-")) {
                vendorAfter++;
            }
        }
        if (vendorAfter < vendorAliases) {
            throw new IOException("Vendor aliases were removed from " + home);
        }
        lines.accept("Synced " + home + " added " + added + ", vendor aliases " + vendorAfter);
    }

    private static String fingerprintsOf(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return "none";
        }
        List<String> prints = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".pem")).sorted().toList()) {
                prints.add(Integer.toHexString(Files.readString(file).hashCode()));
            }
        }
        return prints.isEmpty() ? "none" : String.join(",", prints);
    }

    public List<Path> refreshLocal(Path trusted) throws IOException {
        List<Path> files = new ArrayList<>();
        try (var children = Files.list(trusted.resolve("local"))) {
            for (Path child : children.toList()) {
                if (Files.isRegularFile(child)) {
                    files.add(child);
                }
            }
        }
        return files;
    }

    public byte[] certificate(String pem, Path requested) {
        if (requested != null) {
            Path normal = requested.normalize();
            if (normal.toString().contains("..")) {
                throw new IllegalArgumentException("Refusing path " + requested);
            }
        }
        if (pem.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("Refusing a private key");
        }
        int begin = pem.indexOf("-----BEGIN CERTIFICATE-----");
        int end = pem.indexOf("-----END CERTIFICATE-----");
        if (begin < 0 || end < 0) {
            throw new IllegalArgumentException("PEM has no certificate");
        }
        String body = pem.substring(begin + "-----BEGIN CERTIFICATE-----".length(), end).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public String alias(byte[] der) throws IOException {
        return "jkm-" + sha256(der).substring(0, 8);
    }

    private static String sha256(byte[] der) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    public boolean importOnce(Path keytool, Path cacerts, Path pemFile, String alias) throws IOException, InterruptedException {
        if (aliasExists(keytool, cacerts, alias)) {
            return false;
        }
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-J-Duser.language=en",
                "-importcert",
                "-noprompt",
                "-alias", alias,
                "-file", pemFile.toString(),
                "-keystore", cacerts.toString(),
                "-storepass", "changeit")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException(output);
        }
        return true;
    }

    public boolean aliasExists(Path keytool, Path cacerts, String alias) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-list",
                "-keystore", cacerts.toString(),
                "-alias", alias,
                "-storepass", "changeit")
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    public SourcesDocument check(SourcesDocument document, String fingerprint, boolean checked) {
        List<CertificateChoice> choices = new ArrayList<>(document.certificates() == null ? List.of() : document.certificates());
        choices.removeIf(choice -> choice.fingerprint().equals(fingerprint));
        choices.add(new CertificateChoice(fingerprint, checked));
        return new SourcesDocument(document.urls(), choices);
    }
}
