package dev.jdktrustmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jdktrustmanager.ui.Screens;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeTest {

    @Test
    void applicationListsTheSixScreens() {
        assertEquals(
                List.of(
                        "Catalog",
                        "Installed",
                        "Environment",
                        "Truststore",
                        "Trusted certificates",
                        "Activity"),
                Screens.NAMES);
    }
}
