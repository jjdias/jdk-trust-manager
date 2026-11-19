package dev.jdktrustmanager.trust;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrustScheduleTest {

    @TempDir
    Path directory;

    @Test
    void pauseSkipsAndAStaleBundleIsDue() throws Exception {
        Path marker = directory.resolve("cacert.pem");
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        assertFalse(TrustSchedule.due(marker, true, now));
        assertTrue(TrustSchedule.due(marker, false, now));
        Files.writeString(marker, "pem");
        Files.setLastModifiedTime(marker, FileTime.from(now.minus(1, ChronoUnit.HOURS)));
        assertFalse(TrustSchedule.due(marker, false, now));
        Files.setLastModifiedTime(marker, FileTime.from(now.minus(25, ChronoUnit.HOURS)));
        assertTrue(TrustSchedule.due(marker, false, now));
    }
}
