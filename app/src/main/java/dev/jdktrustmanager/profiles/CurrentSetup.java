package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.JdkRef;
import dev.jdktrustmanager.model.Tool;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** "Save current setup": turns what the user variables point at into a profile draft, explaining anything it can't use. */
public final class CurrentSetup {

    /** {@code jdk} is null when JAVA_HOME isn't a managed JDK; {@code versions} holds only the tools it could capture. */
    public record Captured(JdkRef jdk, Map<Tool, String> versions, List<String> notes) {
    }

    private CurrentSetup() {
    }

    public static Captured capture(Map<String, String> userVariables, List<Installation> managed, Path root) {
        List<String> notes = new ArrayList<>();
        JdkRef jdk = null;
        String javaHome = value(userVariables, "JAVA_HOME");
        if (!javaHome.isBlank()) {
            Installation match = managed.stream().filter(item -> Installations.samePath(item.path(), javaHome)).findFirst().orElse(null);
            if (match != null) {
                jdk = new JdkRef(match.vendorSlug(), match.major());
            } else {
                notes.add("JAVA_HOME (" + javaHome + ") isn't a managed JDK; adopt it on Java → Installed to use it here");
            }
        }
        Map<Tool, String> versions = new EnumMap<>(Tool.class);
        Path folder = ToolLayout.folder(root);
        for (BuildTool tool : BuildTools.ALL) {
            String variable = tool.homeVariables().get(0);
            String home = value(userVariables, variable);
            if (home.isBlank()) {
                continue;
            }
            String version = versionUnder(folder, tool, home);
            if (version != null) {
                versions.put(tool.tool(), version);
            } else {
                notes.add(variable + " points outside build-tools; adopt it on the " + tool.displayName() + " tab to use it here");
            }
        }
        return new Captured(jdk, Map.copyOf(versions), List.copyOf(notes));
    }

    /** The version when {@code home} is {@code <build-tools>\<prefix>-<version>}, otherwise null. */
    private static String versionUnder(Path folder, BuildTool tool, String home) {
        try {
            Path path = Path.of(home.trim()).toAbsolutePath().normalize();
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            String prefix = tool.folderPrefix() + "-";
            if (path.getParent() == null || !Installations.samePath(path.getParent().toString(), folder.toAbsolutePath().normalize().toString())
                    || !name.toLowerCase(Locale.ROOT).startsWith(prefix) || name.length() == prefix.length()) {
                return null;
            }
            return name.substring(prefix.length());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String value(Map<String, String> variables, String name) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null)
                .map(entry -> entry.getValue().trim())
                .findFirst()
                .orElse("");
    }
}
