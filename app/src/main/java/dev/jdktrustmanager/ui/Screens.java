package dev.jdktrustmanager.ui;

import java.util.List;

public final class Screens {

    public static final List<String> NAMES = List.of(
            "Catalog",
            "Installed",
            "Environment",
            "Truststore",
            "Trusted certificates",
            "Activity");

    /** The app's top-level tabs. The Java tab holds {@link #NAMES}. */
    public static final List<String> TOP_LEVEL = List.of("Java", "Maven", "Ant", "Gradle");

    /** Screens inside each build tool tab. */
    public static final List<String> TOOL = List.of("Versions", "Installed", "Environment");

    private Screens() {
    }
}
