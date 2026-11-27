package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolVerifierTest {

    @TempDir
    Path directory;

    static PgpTestKeys release;
    static PgpTestKeys stranger;

    private final Map<String, byte[]> served = new HashMap<>();
    private final Set<String> unreachable = new HashSet<>();
    private final ToolHttp http = url -> {
        if (unreachable.contains(url)) {
            throw new IOException("connect timed out");
        }
        byte[] body = served.get(url);
        if (body == null) {
            throw new NotFoundException(url);
        }
        return body;
    };

    private final ToolRelease maven = new ToolRelease(Tool.MAVEN, "3.3.9", BuildTools.MAVEN.zipUrl("3.3.9"), null, false);
    private final String zipUrl = BuildTools.MAVEN.zipUrl("3.3.9");
    private Path zip;
    private Path keysDir;

    @BeforeAll
    static void keys() throws Exception {
        release = PgpTestKeys.generate("Release", Instant.now().minus(Duration.ofDays(10)), 0, false);
        stranger = PgpTestKeys.generate("Stranger", Instant.now().minus(Duration.ofDays(10)), 0, false);
    }

    @BeforeEach
    void zip() throws IOException {
        zip = directory.resolve("apache-maven-3.3.9-bin.zip");
        Files.writeString(zip, "maven 3.3.9 bytes");
        keysDir = directory.resolve("keys");
    }

    private ToolVerifier verifier() {
        return new ToolVerifier(http, keysDir);
    }

    private void serveKeysAndSignature(PgpTestKeys signer) throws Exception {
        served.put(BuildTools.MAVEN.keysUrl().orElseThrow(), PgpTestKeys.keysFile(List.of(release)));
        served.put(zipUrl + ".asc", signer.sign(zip, Instant.now()));
    }

    private void serveSha1(String hex) {
        served.put(zipUrl + ".sha1", hex.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void validSignatureIsUsed() throws Exception {
        serveKeysAndSignature(release);

        ToolVerifier.Outcome outcome = verifier().verify(BuildTools.MAVEN, maven, zip);

        assertEquals(Verification.SIGNATURE, outcome.used());
        assertTrue(outcome.message().contains(release.signingKeyId()));
        assertTrue(Files.exists(zip));
    }

    @Test
    void tamperedZipIsRejectedAndDeleted() throws Exception {
        serveKeysAndSignature(release);
        Files.writeString(zip, "tampered");

        VerificationException error = assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.MAVEN, maven, zip));

        assertTrue(error.getMessage().startsWith("Signature check failed"));
        assertFalse(Files.exists(zip));
    }

    @Test
    void unknownSignerIsRejectedWithoutFallback() throws Exception {
        serveKeysAndSignature(stranger);
        serveSha1(Checksums.hex(zip, Verification.SHA1));

        VerificationException error = assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.MAVEN, maven, zip));

        assertTrue(error.getMessage().contains("unknown key " + stranger.signingKeyId()));
        assertFalse(Files.exists(zip));
    }

    @Test
    void validSignatureButWrongChecksumIsRejected() throws Exception {
        serveKeysAndSignature(release);
        serveSha1("0000000000000000000000000000000000000000");

        assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.MAVEN, maven, zip));
    }

    @Test
    void keysMissingWithNoCacheFallsBackToSha1WithAWarning() throws Exception {
        served.put(zipUrl + ".asc", release.sign(zip, Instant.now()));
        serveSha1(Checksums.hex(zip, Verification.SHA1));

        ToolVerifier.Outcome outcome = verifier().verify(BuildTools.MAVEN, maven, zip);

        assertEquals(Verification.SHA1, outcome.used());
        assertTrue(outcome.message().contains("SHA-1 only"));
    }

    @Test
    void keysUnreachableUsesTheCachedKeys() throws Exception {
        serveKeysAndSignature(release);
        verifier().verify(BuildTools.MAVEN, maven, zip);
        unreachable.add(BuildTools.MAVEN.keysUrl().orElseThrow());

        ToolVerifier.Outcome outcome = verifier().verify(BuildTools.MAVEN, maven, zip);

        assertEquals(Verification.SIGNATURE, outcome.used());
        assertTrue(Files.exists(keysDir.resolve("MAVEN-KEYS.asc")));
    }

    @Test
    void wrongFallbackChecksumIsRejected() throws Exception {
        serveSha1("0000000000000000000000000000000000000000");

        assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.MAVEN, maven, zip));
        assertFalse(Files.exists(zip));
    }

    @Test
    void nothingReachableIsRejected() {
        VerificationException error = assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.MAVEN, maven, zip));

        assertEquals("No signature or checksum could be downloaded", error.getMessage());
    }

    @Test
    void gradleChecksBySha256() throws Exception {
        Path gradleZip = directory.resolve("gradle-8.10.2-bin.zip");
        Files.writeString(gradleZip, "gradle bytes");
        ToolRelease good = new ToolRelease(Tool.GRADLE, "8.10.2", "https://x.test/g.zip", Checksums.hex(gradleZip, Verification.SHA256), false);
        ToolRelease bad = new ToolRelease(Tool.GRADLE, "8.10.2", "https://x.test/g.zip", "0".repeat(64), false);

        assertEquals(Verification.SHA256, verifier().verify(BuildTools.GRADLE, good, gradleZip).used());
        assertThrows(VerificationException.class, () -> verifier().verify(BuildTools.GRADLE, bad, gradleZip));
        assertFalse(Files.exists(gradleZip));
    }
}
