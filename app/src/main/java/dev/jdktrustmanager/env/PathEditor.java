package dev.jdktrustmanager.env;

import dev.jdktrustmanager.buildtools.BuildTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits the user PATH. The managed block is {@code %JAVA_HOME%\bin} followed by whichever of
 * {@code %MAVEN_HOME%\bin}, {@code %ANT_HOME%\bin}, {@code %GRADLE_HOME%\bin} are present, in that order.
 * JDK cleanup never touches tool entries or anything under {@code <root>\build-tools\}.
 */
public final class PathEditor {

    public static final String MANAGED = "%JAVA_HOME%\\bin";
    public static final List<String> TOOL_ENTRIES = List.of("%MAVEN_HOME%\\bin", "%ANT_HOME%\\bin", "%GRADLE_HOME%\\bin");

    private PathEditor() {
    }

    /** The JDK switch: {@code %JAVA_HOME%\bin} first, then the tool entries, then everything else. */
    public static String edit(String current, String root, boolean removeOtherJava) {
        List<String> rest = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        for (String entry : split(current)) {
            if (isToolVariableEntry(entry)) {
                tools.add(entry);
                continue;
            }
            if (isManaged(entry, root)) {
                continue;
            }
            if (removeOtherJava && isOtherJava(entry)) {
                continue;
            }
            rest.add(entry);
        }
        List<String> next = new ArrayList<>();
        next.add(MANAGED);
        next.addAll(ordered(tools));
        next.addAll(rest);
        return String.join(";", next);
    }

    /**
     * A tool switch. Removes {@code tool}'s entry and {@code otherEntriesToRemove}, then, when {@code add} is true, puts
     * the tool entries right after {@code %JAVA_HOME%\bin} (or first when there is none) in Maven, Ant, Gradle order.
     */
    static String editTool(String current, BuildTool tool, boolean add, List<String> otherEntriesToRemove) {
        List<String> entries = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        for (String entry : split(current)) {
            if (contains(otherEntriesToRemove, entry) || same(entry, tool.pathEntry())) {
                continue;
            }
            if (isToolVariableEntry(entry)) {
                tools.add(entry);
            } else {
                entries.add(entry);
            }
        }
        if (add) {
            tools.add(tool.pathEntry());
        }
        int java = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (same(entries.get(index), MANAGED)) {
                java = index;
                break;
            }
        }
        entries.addAll(java + 1, ordered(tools));
        return String.join(";", entries);
    }

    /** {@code current} without {@code %JAVA_HOME%\bin}; nothing else moves. */
    static String withoutJava(String current) {
        return String.join(";", split(current).stream().filter(entry -> !same(entry, MANAGED)).toList());
    }

    /** The entries "Remove other Java PATH entries" would take out: other JDKs and Oracle's javapath, never tools. */
    public static List<String> otherJavaEntries(String current, String root) {
        return split(current).stream()
                .filter(entry -> !isManaged(entry, root) && !isToolEntry(entry, root) && isOtherJava(entry))
                .toList();
    }

    public static boolean isManaged(String entry, String root) {
        if (isToolEntry(entry, root) || looksLikeTool(entry)) {
            return false;
        }
        String normalized = normalize(entry);
        if (normalized.equalsIgnoreCase(MANAGED)) {
            return true;
        }
        String prefix = withSlash(root);
        return normalized.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))
                && normalized.toLowerCase(Locale.ROOT).endsWith("\\bin");
    }

    public static boolean isOtherJava(String entry) {
        String lower = normalize(entry).toLowerCase(Locale.ROOT);
        if (isToolVariableEntry(entry) || lower.contains("\\build-tools\\") || looksLikeTool(entry)) {
            return false;
        }
        return lower.contains("\\java\\") || lower.contains("javapath") || lower.contains("jdk");
    }

    /** A Maven, Ant, or Gradle folder by name, e.g. {@code C:\Java\apache-maven-3.6.3\bin}. JDK cleanup leaves these alone. */
    static boolean looksLikeTool(String entry) {
        String lower = normalize(entry).toLowerCase(Locale.ROOT);
        return providesMaven(lower) || providesAnt(lower) || providesGradle(lower);
    }

    private static boolean providesMaven(String lower) {
        return lower.contains("%m2_home%") || lower.contains("apache-maven") || lower.contains("\\maven");
    }

    private static boolean providesAnt(String lower) {
        return lower.contains("apache-ant") || lower.contains("\\ant\\") || lower.endsWith("\\ant") || lower.contains("\\ant-");
    }

    private static boolean providesGradle(String lower) {
        return lower.contains("gradle");
    }

    /** A managed tool entry ({@code %MAVEN_HOME%\bin} etc.) or anything under {@code <root>\build-tools\}. */
    public static boolean isToolEntry(String entry, String root) {
        String lower = normalize(entry).toLowerCase(Locale.ROOT);
        return isToolVariableEntry(entry) || lower.startsWith((withSlash(root) + "build-tools\\").toLowerCase(Locale.ROOT));
    }

    /**
     * Entries that provide {@code tool} but aren't the managed one: {@code %M2_HOME%\bin}, {@code C:\apache-maven-3.3.9\bin},
     * Chocolatey or Scoop folders, and the like. Matched by name only; nothing on disk is read.
     */
    public static List<String> otherEntriesFor(String currentPath, String root, BuildTool tool) {
        List<String> others = new ArrayList<>();
        for (String entry : split(currentPath)) {
            if (isToolEntry(entry, root)) {
                continue;
            }
            String lower = normalize(entry).toLowerCase(Locale.ROOT);
            boolean match = switch (tool.tool()) {
                case MAVEN -> providesMaven(lower);
                case ANT -> providesAnt(lower);
                case GRADLE -> providesGradle(lower);
            };
            if (match) {
                others.add(entry);
            }
        }
        return others;
    }

    static List<String> split(String current) {
        List<String> entries = new ArrayList<>();
        if (current == null || current.isBlank()) {
            return entries;
        }
        for (String entry : current.split(";")) {
            if (!entry.isBlank()) {
                entries.add(entry.trim());
            }
        }
        return entries;
    }

    private static boolean isToolVariableEntry(String entry) {
        return contains(TOOL_ENTRIES, entry);
    }

    private static List<String> ordered(List<String> tools) {
        List<String> ordered = new ArrayList<>();
        for (String managed : TOOL_ENTRIES) {
            if (contains(tools, managed)) {
                ordered.add(managed);
            }
        }
        return ordered;
    }

    private static boolean contains(List<String> entries, String entry) {
        return entries.stream().anyMatch(item -> same(item, entry));
    }

    private static boolean same(String left, String right) {
        return strip(normalize(left)).equalsIgnoreCase(strip(normalize(right)));
    }

    private static String strip(String entry) {
        String text = entry;
        while (text.length() > 3 && text.endsWith("\\")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String normalize(String entry) {
        return entry.trim().replace('/', '\\');
    }

    private static String withSlash(String root) {
        String prefix = root.replace('/', '\\');
        return prefix.endsWith("\\") ? prefix : prefix + "\\";
    }
}
