package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.CertificateEntry;
import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportTest {

    @TempDir
    Path directory;

    @Test
    void reportIncludesAdoptedTemurinAndTheLogShowsInstallAndSync() throws Exception {
        Installation adopted = new Installation(
                "temurin", 21, "", "21.0.13", false, "C:\\Java\\temurin-21", "x64", "Eclipse Adoptium", "21.0.13",
                "", Instant.now(), InstallSource.ADOPTED, true, null, null, false, "");
        String html = new HtmlReport().render(List.of(adopted), "C:\\Java\\temurin-21", List.of());
        assertTrue(html.contains("C:\\Java\\temurin-21"));
        assertTrue(html.contains("21"));

        Path log = directory.resolve("work.log");
        try (WorkQueue queue = new WorkQueue(log)) {
            queue.submit(new WorkQueue.Job() {
                @Override
                public WorkQueue.Kind kind() {
                    return WorkQueue.Kind.INSTALL;
                }

                @Override
                public void run(WorkQueue.Progress progress) {
                    progress.log("install temurin-21");
                    progress.log("sync trusted certificates");
                }
            }).get();
        }
        String text = Files.readString(log);
        assertTrue(text.contains("install temurin-21"));
        assertTrue(text.contains("sync trusted certificates"));
    }

    @Test
    void escapesValuesAndListsOnlyCertificatesExpiringSoon() {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        CertificateEntry soon = certificate("jkm-<script>", now.plus(10, ChronoUnit.DAYS));
        CertificateEntry expired = certificate("old-root", now.minus(1, ChronoUnit.DAYS));
        CertificateEntry later = certificate("fine-root", now.plus(400, ChronoUnit.DAYS));

        String html = new HtmlReport().render(List.of(), "C:\\Java\\a&b", "C:\\Java\\a&b\\lib\\security\\cacerts",
                List.of(soon, expired, later), now);

        assertTrue(html.contains("jkm-&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("C:\\Java\\a&amp;b"));
        assertTrue(html.contains("old-root"));
        assertTrue(html.contains("(expired)"));
        assertFalse(html.contains("fine-root"));
        assertTrue(html.contains("3 certificates checked"));
        assertTrue(html.indexOf("old-root") < html.indexOf("jkm-&lt;script&gt;"));
    }

    private static CertificateEntry certificate(String alias, Instant notAfter) {
        return new CertificateEntry(alias, "CN=" + alias, "CN=issuer", "1", "SHA256withRSA", "ab", null, notAfter, "trustedCertEntry", true);
    }
}
