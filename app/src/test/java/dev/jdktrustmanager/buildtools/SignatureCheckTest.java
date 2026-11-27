package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignatureCheckTest {

    @TempDir
    Path directory;

    static PgpTestKeys release;
    static PgpTestKeys other;

    @BeforeAll
    static void keys() throws Exception {
        release = PgpTestKeys.generate("Release", Instant.now().minus(Duration.ofDays(10)), 0, false);
        other = PgpTestKeys.generate("Stranger", Instant.now().minus(Duration.ofDays(10)), 0, false);
    }

    private Path zip(String content) throws IOException {
        Path file = directory.resolve("apache-maven-3.3.9-bin.zip");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void validSignatureFromAKeyInKeys() throws Exception {
        Path file = zip("release bytes");
        byte[] signature = release.sign(file, Instant.now());

        SignatureCheck.Result result = SignatureCheck.verify(file, signature, PgpTestKeys.keysFile(List.of(other, release)));

        assertTrue(result.valid());
        assertTrue(result.signerKnown());
        assertEquals(release.signingKeyId(), result.keyId());
    }

    @Test
    void fileChangedAfterSigningIsInvalid() throws Exception {
        Path file = zip("release bytes");
        byte[] signature = release.sign(file, Instant.now());
        Files.writeString(file, "tampered bytes");

        SignatureCheck.Result result = SignatureCheck.verify(file, signature, PgpTestKeys.keysFile(List.of(release)));

        assertTrue(result.signerKnown());
        assertFalse(result.valid());
    }

    @Test
    void signerNotInKeysIsUnknown() throws Exception {
        Path file = zip("release bytes");
        byte[] signature = other.sign(file, Instant.now());

        SignatureCheck.Result result = SignatureCheck.verify(file, signature, PgpTestKeys.keysFile(List.of(release)));

        assertFalse(result.signerKnown());
        assertFalse(result.valid());
        assertEquals(other.signingKeyId(), result.keyId());
    }

    @Test
    void expiredKeyStillVerifiesOldReleases() throws Exception {
        Instant created = Instant.now().minus(Duration.ofDays(800));
        PgpTestKeys expired = PgpTestKeys.generate("Retired", created, Duration.ofDays(365).toSeconds(), false);
        Path file = zip("old release");
        byte[] signature = expired.sign(file, created.plus(Duration.ofDays(30)));

        SignatureCheck.Result result = SignatureCheck.verify(file, signature, PgpTestKeys.keysFile(List.of(expired)));

        assertTrue(result.valid());
        assertTrue(result.signerKnown());
    }

    @Test
    void signatureBySubkeyIsFound() throws Exception {
        PgpTestKeys withSub = PgpTestKeys.generate("Subkey", Instant.now().minus(Duration.ofDays(5)), 0, true);
        Path file = zip("release bytes");
        byte[] signature = withSub.sign(file, Instant.now());

        SignatureCheck.Result result = SignatureCheck.verify(file, signature, PgpTestKeys.keysFile(List.of(release, withSub)));

        assertTrue(result.valid());
        assertTrue(result.signerKnown());
        assertEquals(withSub.signingKeyId(), result.keyId());
    }

    @Test
    void garbageSignatureIsAnError() throws Exception {
        Path file = zip("release bytes");

        assertThrows(IOException.class, () -> SignatureCheck.verify(file, "not a signature".getBytes(StandardCharsets.US_ASCII),
                PgpTestKeys.keysFile(List.of(release))));
    }
}
