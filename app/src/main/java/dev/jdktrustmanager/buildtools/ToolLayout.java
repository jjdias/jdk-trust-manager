package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.ToolVersions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** {@code <root>\build-tools\<prefix>-<version>\}: one folder per exact version, never overwritten. */
public final class ToolLayout {

    public static final String FOLDER = "build-tools";
    private static final Pattern SAFE_VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.\\-]*");

    private ToolLayout() {
    }

    public static Path folder(Path root) {
        return root.resolve(FOLDER);
    }

    public static Path destination(Path root, BuildTool tool, String version) {
        if (version == null || !SAFE_VERSION.matcher(version).matches() || version.contains("..")) {
            throw new IllegalArgumentException("Invalid version: " + version);
        }
        return folder(root).resolve(tool.folderPrefix() + "-" + version);
    }

    /** Installed version folders of {@code tool}, newest first. Temp folders (names starting with ".") are skipped. */
    public static List<Path> installed(Path root, BuildTool tool) throws IOException {
        Path folder = folder(root);
        List<Path> homes = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return homes;
        }
        String prefix = tool.folderPrefix() + "-";
        try (var children = Files.list(folder)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (!name.startsWith(".") && name.startsWith(prefix) && Files.isDirectory(child) && launcher(tool, child) != null) {
                    homes.add(child);
                }
            }
        }
        homes.sort((left, right) -> ToolVersions.NEWEST_FIRST.compare(versionOf(tool, left), versionOf(tool, right)));
        return homes;
    }

    /** The version in a version folder's name, e.g. {@code 3.9.9} for {@code maven-3.9.9}. */
    public static String versionOf(BuildTool tool, Path home) {
        return home.getFileName().toString().substring(tool.folderPrefix().length() + 1);
    }

    /** The first launcher from {@link BuildTool#launchers()} that exists in {@code home}, or null. */
    public static Path launcher(BuildTool tool, Path home) {
        for (String launcher : tool.launchers()) {
            Path path = home.resolve(launcher);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

}
