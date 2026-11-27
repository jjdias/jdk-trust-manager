package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The download check agreed with the user:
 * <ul>
 * <li>Apache (Maven, Ant): a valid signature by a key in KEYS is required when the keys and signature can be fetched.
 * A bad signature or an unknown signer is rejected, with no fallback.</li>
 * <li>Only when KEYS (live and cached) or the {@code .asc} can't be fetched: the strongest published checksum, with a
 * warning when that is SHA-1 or MD5.</li>
 * <li>Gradle: the SHA-256 from the version list.</li>
 * </ul>
 * A rejected zip is deleted.
 */
public final class ToolVerifier {

    public record Outcome(Verification used, String message) {
    }

    private final ToolHttp http;
    private final Path keysDir;

    public ToolVerifier(ToolHttp http, Path keysDir) {
        this.http = http;
        this.keysDir = keysDir;
    }

    public Outcome verify(BuildTool tool, ToolRelease release, Path zip) throws IOException {
        if (tool.keysUrl().isEmpty()) {
            return sha256(release, zip);
        }
        Optional<byte[]> keys = keys(tool);
        Optional<byte[]> signature = tool.signatureUrl(release.version()).flatMap(this::fetch);
        if (keys.isPresent() && signature.isPresent()) {
            return signed(tool, release, zip, keys.get(), signature.get());
        }
        return checksumOnly(tool, release, zip);
    }

    private Outcome sha256(ToolRelease release, Path zip) throws IOException {
        if (release.sha256() == null || !release.sha256().equalsIgnoreCase(Checksums.hex(zip, Verification.SHA256))) {
            throw reject(zip, "SHA-256 mismatch");
        }
        return new Outcome(Verification.SHA256, "SHA-256 verified");
    }

    private Outcome signed(BuildTool tool, ToolRelease release, Path zip, byte[] keys, byte[] signature) throws IOException {
        SignatureCheck.Result result;
        try {
            result = SignatureCheck.verify(zip, signature, keys);
        } catch (IOException ex) {
            throw reject(zip, "Signature check failed: " + ex.getMessage());
        }
        if (!result.signerKnown()) {
            throw reject(zip, "Signed by unknown key " + result.keyId());
        }
        if (!result.valid()) {
            throw reject(zip, "Signature check failed (key " + result.keyId() + ")");
        }
        Optional<Match> checksum = strongestChecksum(tool, release, zip);
        if (checksum.isPresent() && !checksum.get().matches()) {
            throw reject(zip, Checksums.label(checksum.get().kind()) + " mismatch");
        }
        return new Outcome(Verification.SIGNATURE, "Signature verified (key " + result.keyId() + ")");
    }

    private Outcome checksumOnly(BuildTool tool, ToolRelease release, Path zip) throws IOException {
        Optional<Match> checksum = strongestChecksum(tool, release, zip);
        if (checksum.isEmpty()) {
            throw reject(zip, "No signature or checksum could be downloaded");
        }
        if (!checksum.get().matches()) {
            throw reject(zip, Checksums.label(checksum.get().kind()) + " mismatch");
        }
        Verification kind = checksum.get().kind();
        String message = kind.weak()
                ? "Verified with " + Checksums.label(kind) + " only: Apache's signing keys couldn't be reached"
                : "Verified with " + Checksums.label(kind) + ": Apache's signing keys couldn't be reached";
        return new Outcome(kind, message);
    }

    private record Match(Verification kind, boolean matches) {
    }

    /** The first checksum file, strongest first, that downloads and contains a hash of the right length. */
    private Optional<Match> strongestChecksum(BuildTool tool, ToolRelease release, Path zip) throws IOException {
        for (Checksum checksum : tool.checksumUrls(release.version())) {
            Optional<String> expected = fetch(checksum.url())
                    .flatMap(body -> Checksums.extract(new String(body, StandardCharsets.US_ASCII), checksum.kind()));
            if (expected.isPresent()) {
                return Optional.of(new Match(checksum.kind(), expected.get().equalsIgnoreCase(Checksums.hex(zip, checksum.kind()))));
            }
        }
        return Optional.empty();
    }

    /** Live KEYS, saved for later; the saved copy when the live one can't be fetched. */
    private Optional<byte[]> keys(BuildTool tool) {
        Path cached = keysDir.resolve(tool.tool().name() + "-KEYS.asc");
        Optional<byte[]> live = fetch(tool.keysUrl().orElseThrow());
        if (live.isPresent()) {
            try {
                Files.createDirectories(keysDir);
                Files.write(cached, live.get());
            } catch (IOException ignored) {
                // Verification can still use the live copy.
            }
            return live;
        }
        try {
            return Files.isRegularFile(cached) ? Optional.of(Files.readAllBytes(cached)) : Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Optional<byte[]> fetch(String url) {
        try {
            return Optional.of(http.get(url));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static VerificationException reject(Path zip, String message) {
        try {
            Files.deleteIfExists(zip);
        } catch (IOException ignored) {
            // The message still reaches the user; the cache folder is cleaned on the next attempt.
        }
        return new VerificationException(message);
    }
}
