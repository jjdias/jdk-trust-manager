package dev.jdktrustmanager.trust;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class TrustSchedule {

    public static final long MAX_AGE_HOURS = 24;

    private TrustSchedule() {
    }

    public static boolean due(Path marker, boolean pause, Instant now) {
        if (pause) {
            return false;
        }
        if (marker == null || Files.notExists(marker)) {
            return true;
        }
        try {
            Instant modified = Files.getLastModifiedTime(marker).toInstant();
            return modified.isBefore(now.minus(Duration.ofHours(MAX_AGE_HOURS)));
        } catch (IOException ex) {
            return true;
        }
    }
}
