package dev.jdktrustmanager.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.Verification;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumsTest {

    private static final String SHA1 = "9d8a3a6d4bbbdd5e7c4f1b6a3c2e1f0a9b8c7d6e";

    @TempDir
    Path directory;

    @Test
    void readsTheCommonChecksumFileFormats() {
        assertEquals(SHA1, Checksums.extract(SHA1, Verification.SHA1).orElseThrow());
        assertEquals(SHA1, Checksums.extract(SHA1.toUpperCase() + "  apache-ant-1.7.1-bin.zip\n", Verification.SHA1).orElseThrow());
        assertEquals(SHA1, Checksums.extract(SHA1 + " *apache-ant-1.7.1-bin.zip", Verification.SHA1).orElseThrow());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                Checksums.extract("MD5 (apache-ant-1.6.5-bin.zip) = d41d8cd98f00b204e9800998ecf8427e", Verification.MD5).orElseThrow());
    }

    @Test
    void wrongLengthIsNotAMatch() {
        assertTrue(Checksums.extract(SHA1, Verification.SHA512).isEmpty());
    }

    @Test
    void hashesAFile() throws Exception {
        Path empty = Files.createFile(directory.resolve("empty"));

        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Checksums.hex(empty, Verification.MD5));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Checksums.hex(empty, Verification.SHA256));
    }
}
