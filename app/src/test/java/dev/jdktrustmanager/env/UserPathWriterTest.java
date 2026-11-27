package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jdktrustmanager.buildtools.BuildTools;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserPathWriterTest {

    private final List<EnvironmentPlan> applied = new ArrayList<>();

    private UserPathWriter writer(Optional<String> path) {
        return new UserPathWriter(new UserPathWriter.Registry() {
            @Override
            public Optional<String> userPath() {
                return path;
            }

            @Override
            public void apply(EnvironmentPlan plan) {
                applied.add(plan);
            }
        });
    }

    @Test
    void buildsFromThePathAtWriteTimeSoEarlierSwitchesSurvive() throws Exception {
        // The screen previewed "C:\Windows"; since then Maven was set from the Maven tab.
        String now = "%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;C:\\Windows";

        EnvironmentPlan plan = writer(Optional.of(now))
                .write(current -> EnvironmentPlan.forJava(current, "C:\\Java", "C:\\Java\\temurin-17", false));

        assertEquals("%JAVA_HOME%\\bin;%MAVEN_HOME%\\bin;C:\\Windows", plan.path());
        assertEquals(List.of(plan), applied);
    }

    @Test
    void anUnreadablePathIsNeverOverwritten() {
        IOException error = assertThrows(IOException.class, () -> writer(Optional.empty())
                .write(current -> EnvironmentPlan.forTool(current, "C:\\Java", BuildTools.MAVEN, null, List.of())));

        assertTrue(error.getMessage().contains("couldn't be read"));
        assertTrue(applied.isEmpty());
    }

    @Test
    void aUserWithNoPathValueStillGetsTheToolEntry() throws Exception {
        EnvironmentPlan plan = writer(Optional.of(""))
                .write(current -> EnvironmentPlan.forTool(current, "C:\\Java", BuildTools.ANT, "C:\\Java\\build-tools\\ant-1.10.15", List.of()));

        assertEquals("%ANT_HOME%\\bin", plan.path());
    }

    @Test
    void registryReadDistinguishesFailureFromNoPath() {
        assertEquals(Optional.empty(), UserPathWriter.pathFrom(Optional.empty()));
        assertEquals(Optional.of(""), UserPathWriter.pathFrom(Optional.of(Map.of("TEMP", "x"))));
        assertEquals(Optional.of("C:\\Windows"), UserPathWriter.pathFrom(Optional.of(Map.of("PATH", "C:\\Windows"))));
    }
}
