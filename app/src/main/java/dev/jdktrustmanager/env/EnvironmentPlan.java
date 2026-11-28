package dev.jdktrustmanager.env;

import dev.jdktrustmanager.buildtools.BuildTool;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One user-environment change: variables to set ({@code REG_SZ}), variables to delete, and the new {@code Path}
 * ({@code REG_EXPAND_SZ}). The JDK switch and the tool switches build these; profiles will combine them.
 */
public record EnvironmentPlan(Map<String, String> set, Set<String> delete, String path) {

    public EnvironmentPlan {
        set = Map.copyOf(set);
        delete = Set.copyOf(delete);
    }

    public static EnvironmentPlan forJava(String currentPath, String root, String javaHome, boolean removeOtherJava) {
        return new EnvironmentPlan(Map.of("JAVA_HOME", javaHome), Set.of(), PathEditor.edit(currentPath, root, removeOtherJava));
    }

    /**
     * A whole profile in one write: JAVA_HOME, then each of Maven, Ant, and Gradle either on PATH with its variables
     * (home given) or off PATH with its variables deleted (null or absent). "Remove other Java entries" isn't applied.
     */
    public static EnvironmentPlan forProfile(String currentPath, String root, String javaHome, Map<BuildTool, String> toolHomesOrNull) {
        Map<String, String> set = new LinkedHashMap<>();
        Set<String> delete = new LinkedHashSet<>();
        set.put("JAVA_HOME", javaHome);
        String path = PathEditor.edit(currentPath, root, false);
        for (BuildTool tool : dev.jdktrustmanager.buildtools.BuildTools.ALL) {
            String home = toolHomesOrNull.get(tool);
            for (String variable : tool.homeVariables()) {
                if (home == null) {
                    delete.add(variable);
                } else {
                    set.put(variable, home);
                }
            }
            path = PathEditor.editTool(path, tool, home != null, List.of());
        }
        return new EnvironmentPlan(set, delete, path);
    }

    /** Takes Java off the user PATH: deletes JAVA_HOME and {@code %JAVA_HOME%\bin}; tool entries and everything else stay. */
    public static EnvironmentPlan withoutJava(String currentPath) {
        return new EnvironmentPlan(Map.of(), Set.of("JAVA_HOME"), PathEditor.withoutJava(currentPath));
    }

    /** {@code homeOrNull == null} takes the tool off PATH and deletes its variables. */
    public static EnvironmentPlan forTool(String currentPath, String root, BuildTool tool, String homeOrNull,
            List<String> otherEntriesToRemove) {
        Map<String, String> set = new LinkedHashMap<>();
        Set<String> delete = new LinkedHashSet<>();
        for (String variable : tool.homeVariables()) {
            if (homeOrNull == null) {
                delete.add(variable);
            } else {
                set.put(variable, homeOrNull);
            }
        }
        return new EnvironmentPlan(set, delete, PathEditor.editTool(currentPath, tool, homeOrNull != null, otherEntriesToRemove));
    }
}
