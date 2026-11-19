package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
}
