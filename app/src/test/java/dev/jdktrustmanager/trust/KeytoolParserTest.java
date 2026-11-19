package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.model.CertificateEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeytoolParserTest {

    @Test
    void fixtureParsesWithoutKeytoolAndTemurinFillsTheTable() throws Exception {
        String fixture;
        try (var input = KeytoolParserTest.class.getResourceAsStream("/trust/keytool-list.txt")) {
            fixture = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<CertificateEntry> parsed = new KeytoolParser().parse(fixture);
        assertEquals("example", parsed.get(0).alias());
        assertEquals("AABBCC", parsed.get(0).sha256());
        assertEquals("SHA256withRSA", parsed.get(0).signatureAlgorithm());
        assertEquals("private key present", parsed.get(1).entryType());
        assertFalse(parsed.get(1).entryType().contains("BEGIN"));

        Path home = Path.of("C:\\Java\\temurin-21");
        if (!Files.isRegularFile(home.resolve("bin").resolve("keytool.exe"))) {
            return;
        }
        List<CertificateEntry> live = new KeytoolStore().listCacerts(home, 21);
        assertFalse(live.isEmpty());
        assertTrue(live.stream().anyMatch(entry -> entry.alias() != null && !entry.alias().isBlank()));
    }
}
