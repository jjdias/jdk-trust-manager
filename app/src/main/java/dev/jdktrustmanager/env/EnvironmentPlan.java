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
