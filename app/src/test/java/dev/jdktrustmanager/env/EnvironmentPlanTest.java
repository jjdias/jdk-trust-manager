package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.buildtools.BuildTools;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnvironmentPlanTest {

    private static final String ROOT = "C:\\Java";

    @Test
    void jdkSwitchKeepsToolEntriesRightAfterJava() {
        EnvironmentPlan plan = EnvironmentPlan.forJava("%MAVEN_HOME%\\bin;C:\\Windows", ROOT, "C:\\Java\\temurin-21", false);

        assertEquals("%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;C:\\Windows", plan.path());
        assertEquals(Map.of("JAVA_HOME", "C:\\Java\\temurin-21"), plan.set());
        assertEquals(Set.of(), plan.delete());
    }

    @Test
    void jdkCleanupNeverDropsBuildToolsFolders() {
        EnvironmentPlan plan = EnvironmentPlan.forJava(
                "C:\\Java\\temurin-17\\bin;C:\\Java\\build-tools\\ant-1.10.15\\bin;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath",
                ROOT, "C:\\Java\\temurin-21", true);

        assertEquals("%JAVA_HOME%\\bin;C:\\Java\\build-tools\\ant-1.10.15\\bin", plan.path());
    }

    @Test
    void jdkSwitchKeepsHandMadeToolFoldersUnderTheRoot() {
        String current = "C:\\Java\\apache-maven-3.6.3\\bin;C:\\Java\\temurin-17\\bin;C:\\Java\\gradle-7.6\\bin;C:\\Java\\ant-1.9.16\\bin";

        assertEquals("%JAVA_HOME%\\bin;C:\\Java\\apache-maven-3.6.3\\bin;C:\\Java\\gradle-7.6\\bin;C:\\Java\\ant-1.9.16\\bin",
                EnvironmentPlan.forJava(current, ROOT, "C:\\Java\\temurin-21", false).path());
        assertEquals("%JAVA_HOME%\\bin;C:\\Java\\apache-maven-3.6.3\\bin;C:\\Java\\gradle-7.6\\bin;C:\\Java\\ant-1.9.16\\bin",
                EnvironmentPlan.forJava(current, ROOT, "C:\\Java\\temurin-21", true).path());
    }

    @Test
    void removingJavaFromPathKeepsTheToolsAndEverythingElse() {
        EnvironmentPlan plan = EnvironmentPlan.withoutJava("%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;C:\\Windows;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath");

        assertEquals("%MAVEN_HOME%\\bin;C:\\Windows;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath", plan.path());
        assertEquals(Set.of("JAVA_HOME"), plan.delete());
        assertEquals(Map.of(), plan.set());
    }

    @Test
    void otherJavaEntriesAreListedButNotToolsOrTheManagedEntry() {
        String current = "%JAVA_HOME%\\bin;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath;C:\\Java\\apache-maven-3.6.3\\bin;"
                + "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.13.11-hotspot\\bin;C:\\Windows";

        assertEquals(List.of("C:\\Program Files\\Common Files\\Oracle\\Java\\javapath",
                "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.13.11-hotspot\\bin"),
                PathEditor.otherJavaEntries(current, ROOT));
    }

    @Test
    void mavenSwitchGoesAfterJavaAndBeforeAnt() {
        EnvironmentPlan plan = EnvironmentPlan.forTool("%JAVA_HOME%\\bin;%ANT_HOME%\\bin;C:\\Windows", ROOT, BuildTools.MAVEN,
                "C:\\Java\\build-tools\\maven-3.3.9", List.of());

        assertEquals("%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;%ANT_HOME%\\bin;C:\\Windows", plan.path());
        assertEquals(Map.of("MAVEN_HOME", "C:\\Java\\build-tools\\maven-3.3.9", "M2_HOME", "C:\\Java\\build-tools\\maven-3.3.9"), plan.set());
    }

    @Test
    void toolSwitchLeavesTheJavaEntryWhereItIs() {
        EnvironmentPlan plan = EnvironmentPlan.forTool("C:\\Windows;%JAVA_HOME%\\bin;C:\\tools", ROOT, BuildTools.GRADLE,
                "C:\\Java\\build-tools\\gradle-8.10.2", List.of());

        assertEquals("C:\\Windows;%JAVA_HOME%\\bin;%GRADLE_HOME%\\bin;C:\\tools", plan.path());
    }

    @Test
    void toolSwitchWithoutAJavaEntryGoesFirst() {
        EnvironmentPlan plan = EnvironmentPlan.forTool("C:\\Windows", ROOT, BuildTools.MAVEN, "C:\\Java\\build-tools\\maven-3.9.9", List.of());

        assertEquals("%MAVEN_HOME%\\bin;C:\\Windows", plan.path());
    }

    @Test
    void switchingAgainDoesNotDuplicateTheEntry() {
        String once = EnvironmentPlan.forTool("%JAVA_HOME%\\bin;C:\\Windows", ROOT, BuildTools.MAVEN, "C:\\Java\\build-tools\\maven-3.9.9", List.of()).path();

        assertEquals(once, EnvironmentPlan.forTool(once, ROOT, BuildTools.MAVEN, "C:\\Java\\build-tools\\maven-3.3.9", List.of()).path());
    }

    @Test
    void removeFromPathDeletesOnlyThatTool() {
        EnvironmentPlan plan = EnvironmentPlan.forTool("%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;%ANT_HOME%\\bin;C:\\Windows", ROOT,
                BuildTools.MAVEN, null, List.of());

        assertEquals("%JAVA_HOME%\\bin;%ANT_HOME%\\bin;C:\\Windows", plan.path());
        assertEquals(Set.of("MAVEN_HOME", "M2_HOME"), plan.delete());
        assertEquals(Map.of(), plan.set());
    }

    @Test
    void legacyEntriesAreFoundButOnlyRemovedWhenPassed() {
        String current = "%M2_HOME%\\bin;C:\\apache-maven-3.3.9\\bin;C:\\ant\\bin;C:\\Java\\build-tools\\maven-3.9.9\\bin;%MAVEN_HOME%\\bin";

        List<String> others = PathEditor.otherEntriesFor(current, ROOT, BuildTools.MAVEN);

        assertEquals(List.of("%M2_HOME%\\bin", "C:\\apache-maven-3.3.9\\bin"), others);
        String kept = EnvironmentPlan.forTool(current, ROOT, BuildTools.MAVEN, "C:\\Java\\build-tools\\maven-3.9.9", List.of()).path();
        assertTrue(kept.contains("%M2_HOME%\\bin") && kept.contains("C:\\apache-maven-3.3.9\\bin"));
        String cleaned = EnvironmentPlan.forTool(current, ROOT, BuildTools.MAVEN, "C:\\Java\\build-tools\\maven-3.9.9", others).path();
        assertEquals("%MAVEN_HOME%\\bin;C:\\ant\\bin;C:\\Java\\build-tools\\maven-3.9.9\\bin", cleaned);
        assertEquals(List.of("C:\\ant\\bin"), PathEditor.otherEntriesFor(current, ROOT, BuildTools.ANT));
    }

    @Test
    void javaListingIgnoresToolEntries() {
        List<String> entries = JavaVariables.javaPathEntries(
                "%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;C:\\Java\\build-tools\\gradle-8.10.2\\bin;C:\\Java\\temurin-17\\bin", ROOT);

        assertEquals(List.of("%JAVA_HOME%\\bin", "C:\\Java\\temurin-17\\bin"), entries);
    }
}
