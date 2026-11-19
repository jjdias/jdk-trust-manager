package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustSyncTest {

    @TempDir
    Path directory;

    @Test
    void importsOnceAndRejectsPrivateKeysAndParentPaths() throws Exception {
        Path home = Path.of(System.getProperty("java.home"));
        Path keytool = home.resolve("bin").resolve("keytool.exe");
        Path cacerts = home.resolve("lib").resolve("security").resolve("cacerts");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(keytool) && Files.isRegularFile(cacerts));

        TrustSync sync = new TrustSync();
        Path trusted = sync.ensureLayout(directory, new JsonStores());
        Path store = directory.resolve("ks.p12");
        Path pem = directory.resolve("cert.pem");
        exec(keytool, "-genkeypair", "-alias", "tmp", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=JKMTest", "-keystore", store.toString(), "-storepass", "changeit", "-storetype", "PKCS12");
        exec(keytool, "-exportcert", "-rfc", "-alias", "tmp", "-keystore", store.toString(), "-storepass", "changeit", "-file", pem.toString());
        String text = Files.readString(pem);
        String alias = sync.alias(sync.certificate(text, pem));
        Path copy = directory.resolve("cacerts");
        Files.copy(cacerts, copy);
        assertTrue(sync.importOnce(keytool, copy, pem, alias));
        assertFalse(sync.importOnce(keytool, copy, pem, alias));
        assertTrue(sync.aliasExists(keytool, copy, alias));

        assertThrows(IllegalArgumentException.class, () -> sync.certificate("-----BEGIN PRIVATE KEY-----\nMII=\n-----END PRIVATE KEY-----", null));
        assertThrows(IllegalArgumentException.class, () -> sync.certificate(text, Path.of("..\\evil.pem")));

        Files.writeString(trusted.resolve("local").resolve("dropped.pem"), text);
        assertEquals(1, sync.refreshLocal(trusted).size());
        assertEquals(2, sync.certificatesIn(text + "\n" + text).size());
        assertThrows(java.io.IOException.class, () -> new dev.jdktrustmanager.trust.TrustDownloads().get(java.net.URI.create("http://example.com/ca.pem")));
    }

    private static void exec(Path keytool, String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(keytool.toString());
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
    }
}
