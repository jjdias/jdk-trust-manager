package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.jdktrustmanager.model.InstallSource;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Tool;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CurrentSetupTest {

    private static final Path ROOT = Path.of("C:\\Java");
    private static final List<Installation> MANAGED = List.of(new Installation("temurin", 8, "", "1.8.0_432", false,
            "C:\\Java\\temurin-8", "x64", "", "", "", null, InstallSource.FOOJAY, true, null, null, false, ""));

    @Test
    void managedJdkAndBuildToolsHomesAreCaptured() {
        CurrentSetup.Captured captured = CurrentSetup.capture(Map.of(
                "JAVA_HOME", "C:\\Java\\temurin-8",
                "MAVEN_HOME", "C:\\Java\\build-tools\\maven-3.3.9",
                "ANT_HOME", "c:\\java\\build-tools\\ant-1.9.16\\"), MANAGED, ROOT);

        assertEquals(new JdkRef("temurin", 8), captured.jdk());
        assertEquals(Map.of(Tool.MAVEN, "3.3.9", Tool.ANT, "1.9.16"), captured.versions());
        assertEquals(List.of(), captured.notes());
    }

    @Test
    void aToolOutsideBuildToolsIsLeftOutWithAReason() {
        CurrentSetup.Captured captured = CurrentSetup.capture(Map.of(
                "JAVA_HOME", "C:\\Java\\temurin-8", "MAVEN_HOME", "C:\\apache-maven-3.3.9"), MANAGED, ROOT);

        assertEquals(Map.of(), captured.versions());
        assertEquals(List.of("MAVEN_HOME points outside build-tools; adopt it on the Maven tab to use it here"), captured.notes());
    }

    @Test
    void anUnmanagedJavaHomeIsLeftOutWithAReason() {
        CurrentSetup.Captured captured = CurrentSetup.capture(Map.of("JAVA_HOME", "C:\\Program Files\\Zulu\\zulu-17"), MANAGED, ROOT);

        assertNull(captured.jdk());
        assertEquals(List.of("JAVA_HOME (C:\\Program Files\\Zulu\\zulu-17) isn't a managed JDK; adopt it on Java → Installed to use it here"),
                captured.notes());
    }

    @Test
    void nothingSetCapturesNothing() {
        CurrentSetup.Captured captured = CurrentSetup.capture(Map.of(), MANAGED, ROOT);

        assertNull(captured.jdk());
        assertEquals(Map.of(), captured.versions());
        assertEquals(List.of(), captured.notes());
    }
}
