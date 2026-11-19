package dev.jdktrustmanager.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JavaVariables {

    public static final List<String> NAMES = List.of(
            "JAVA_HOME",
            "JDK_HOME",
            "JRE_HOME",
            "CLASSPATH",
            "JAVA_TOOL_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "_JAVA_OPTIONS");

    public record Row(String name, String scope, String value) {
    }

    private JavaVariables() {
    }

    public static List<Row> list(Map<String, String> user, Map<String, String> machine, String root) {
        List<Row> rows = new ArrayList<>();
        for (String name : NAMES) {
            add(rows, name, "User", find(user, name));
            add(rows, name, "System", find(machine, name));
        }
        for (String entry : javaPathEntries(find(user, "Path"), root)) {
            rows.add(new Row("Path", "User", entry));
        }
        for (String entry : javaPathEntries(find(machine, "Path"), root)) {
            rows.add(new Row("Path", "System", entry));
        }
        return rows;
    }

    public static String find(Map<String, String> values, String name) {
        if (values == null) {
            return "";
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return "";
    }

    public static List<String> javaPathEntries(String path, String root) {
        List<String> entries = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return entries;
        }
        for (String entry : path.split(";")) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (PathEditor.isManaged(value, root) || PathEditor.isOtherJava(value)) {
                entries.add(value);
            }
        }
        return entries;
    }

    private static void add(List<Row> rows, String name, String scope, String value) {
        if (value != null && !value.isBlank()) {
            rows.add(new Row(name, scope, value));
        }
    }
}
