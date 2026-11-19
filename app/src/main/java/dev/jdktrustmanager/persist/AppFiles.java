package dev.jdktrustmanager.persist;

import java.nio.file.Path;

public final class AppFiles {

    private AppFiles() {
    }

    public static Path stateDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            local = System.getProperty("user.home");
        }
        return Path.of(local, "JdkTrustManager");
    }

    public static Path catalog() {
        return stateDir().resolve("catalog.json");
    }

    public static Path installations() {
        return stateDir().resolve("installations.json");
    }

    public static Path settings() {
        return stateDir().resolve("settings.json");
    }

    public static Path logs() {
        return stateDir().resolve("logs").resolve("work.log");
    }

    public static Path zipCache() {
        return stateDir().resolve("cache");
    }
}
