package dev.jdktrustmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jdktrustmanager.ui.Screens;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeTest {

    @Test
    void javaTabHoldsTheJavaScreens() {
        assertEquals(
                List.of(
                        "Catalog",
                        "Installed",
                        "Environment",
                        "Truststore",
                        "Trusted certificates"),
                Screens.NAMES);
    }

    @Test
    void topLevelTabsAndToolScreens() {
        assertEquals(List.of("Java", "Maven", "Ant", "Gradle", "Profiles", "Activity"), Screens.TOP_LEVEL);
        assertEquals(List.of("Versions", "Installed", "Environment"), Screens.TOOL);
    }
}
