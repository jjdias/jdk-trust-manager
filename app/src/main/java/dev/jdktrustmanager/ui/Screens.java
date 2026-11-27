package dev.jdktrustmanager.ui;

import java.util.List;

public final class Screens {

    public static final List<String> NAMES = List.of(
            "Catalog",
            "Installed",
            "Environment",
            "Truststore",
            "Trusted certificates");

    /** The app's top-level tabs. The Java tab holds {@link #NAMES}; Activity covers every tab's jobs. */
    public static final List<String> TOP_LEVEL = List.of("Java", "Maven", "Ant", "Gradle", "Activity");

    /** Screens inside each build tool tab. */
    public static final List<String> TOOL = List.of("Versions", "Installed", "Environment");

    private Screens() {
    }
}
