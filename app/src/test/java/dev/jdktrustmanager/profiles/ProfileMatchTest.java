package dev.jdktrustmanager.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Profile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileMatchTest {

    private static final Path ROOT = Path.of("C:\\Java");
    private static final String JDK8 = "C:\\Java\\temurin-8";
    private static final String MAVEN = "C:\\Java\\build-tools\\maven-3.3.9";
    private static final String ANT = "C:\\Java\\build-tools\\ant-1.9.16";

    private final Profile a = new Profile("A", new JdkRef("temurin", 8), "3.3.9", null, null);
    private final Profile b = new Profile("B", new JdkRef("temurin", 8), "3.3.9", "1.9.16", null);

    @Test
    void anExactMatchIsActive() {
        assertEquals(Optional.of("A"), ProfileMatch.active(List.of(a), Map.of("JAVA_HOME", JDK8, "MAVEN_HOME", MAVEN), ROOT));
    }

    @Test
    void caseAndTrailingSlashesDoNotMatter() {
        assertEquals(Optional.of("A"),
                ProfileMatch.active(List.of(a), Map.of("java_home", "c:\\java\\TEMURIN-8\\", "Maven_Home", MAVEN + "\\"), ROOT));
    }

    @Test
    void anExtraToolMeansNoMatch() {
        assertEquals(Optional.empty(),
                ProfileMatch.active(List.of(a), Map.of("JAVA_HOME", JDK8, "MAVEN_HOME", MAVEN, "GRADLE_HOME", "C:\\Java\\build-tools\\gradle-8.10.2"), ROOT));
    }

    @Test
    void aBlankVariableCountsAsUnset() {
        assertEquals(Optional.of("A"), ProfileMatch.active(List.of(a), Map.of("JAVA_HOME", JDK8, "MAVEN_HOME", MAVEN, "ANT_HOME", " "), ROOT));
    }

    @Test
    void aDifferentJdkMeansNoMatch() {
        assertEquals(Optional.empty(),
                ProfileMatch.active(List.of(a), Map.of("JAVA_HOME", "C:\\Java\\temurin-21", "MAVEN_HOME", MAVEN), ROOT));
    }

    @Test
    void profilesThatDifferOnlyInAnOmittedToolPickTheRightOne() {
        assertEquals(Optional.of("B"), ProfileMatch.active(List.of(a, b), Map.of("JAVA_HOME", JDK8, "MAVEN_HOME", MAVEN, "ANT_HOME", ANT), ROOT));
        assertEquals(Optional.of("A"), ProfileMatch.active(List.of(b, a), Map.of("JAVA_HOME", JDK8, "MAVEN_HOME", MAVEN), ROOT));
    }

    @Test
    void noProfilesMeansNoneActive() {
        assertEquals(Optional.empty(), ProfileMatch.active(List.of(), Map.of("JAVA_HOME", JDK8), ROOT));
    }
}
