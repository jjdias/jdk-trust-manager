package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.install.ZipInstaller;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolSource;
import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Finds Maven, Ant, and Gradle installs made outside the app and copies them into the layout. */
public final class ToolAdopter {

    private static final Pattern VARIABLE = Pattern.compile("%([^%]+)%");

    /** Folders whose children are commonly tool homes, for {@code tool}. */
    public static List<Path> defaultRoots(BuildTool tool, Map<String, String> environment) {
        String name = tool.folderPrefix();
        List<Path> roots = new ArrayList<>(List.of(
                Path.of("C:\\Program Files\\Apache"),
                Path.of("C:\\Program Files"),
                Path.of("C:\\"),
                Path.of("C:\\tools"),
                Path.of("C:\\ProgramData\\chocolatey\\lib").resolve(name).resolve("tools")));
        String profile = environment.getOrDefault("USERPROFILE", System.getenv("USERPROFILE"));
        if (profile != null && !profile.isBlank()) {
            roots.add(Path.of(profile, "scoop", "apps", name));
        }
        return roots;
    }

    /**
     * Homes named by the tool's variables, by PATH entries holding its launcher, and by children of {@code extraRoots}.
     * Duplicates and anything inside a {@code build-tools} folder are left out.
     */
    public static List<Path> candidates(BuildTool tool, Map<String, String> variables, String path, List<Path> extraRoots) {
        List<Path> found = new ArrayList<>();
        for (String variable : tool.homeVariables()) {
            value(variables, variable).ifPresent(value -> add(found, tool, Path.of(value)));
        }
        if (path != null) {
            for (String entry : path.split(";")) {
                String expanded = expand(entry.trim(), variables);
                if (expanded.isEmpty() || expanded.contains("%")) {
                    continue;
                }
                Path bin = Path.of(expanded);
                if (bin.getParent() != null) {
                    add(found, tool, bin.getParent());
                }
            }
        }
        for (Path root : extraRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory).sorted().forEach(child -> add(found, tool, child));
            } catch (IOException | RuntimeException ignored) {
                // An unreadable folder just contributes nothing.
            }
        }
        return found;
    }

    public static Optional<String> versionOf(BuildTool tool, Path home) {
        Path lib = home.resolve("lib");
        return switch (tool.tool()) {
            case MAVEN -> fromJarName(lib, "maven-core-").or(() -> maven2Uber(lib));
            case GRADLE -> fromJarName(lib, "gradle-core-api-").or(() -> fromJarName(lib, "gradle-launcher-"))
                    .or(() -> fromJarName(lib, "gradle-core-"));
            case ANT -> implementationVersion(lib.resolve("ant.jar"));
        };
    }

    /** Copies {@code home} into {@code <root>\build-tools\<prefix>-<version>} and leaves the original alone. */
    public ToolInstallation adopt(BuildTool tool, Path home, Path root) throws IOException {
        String version = versionOf(tool, home)
                .orElseThrow(() -> new IOException("Couldn't tell which " + tool.displayName() + " version " + home + " is"));
        Path destination = ToolLayout.destination(root, tool, version);
        if (Files.exists(destination)) {
            throw new IOException(tool.displayName() + " " + version + " is already installed");
        }
        Path folder = ToolLayout.folder(root);
        Files.createDirectories(folder);
        Path partial = folder.resolve(".adopt-" + System.currentTimeMillis());
        try {
            copy(home, partial);
            Files.move(partial, destination);
        } finally {
            ZipInstaller.deleteRecursively(partial);
        }
        return new ToolInstallation(tool.tool(), version, destination.toString(), ToolSource.ADOPTED, Verification.NONE,
                Instant.now(), false, false, "");
    }

    private static void add(List<Path> found, BuildTool tool, Path candidate) {
        try {
            Path home = candidate.toAbsolutePath().normalize();
            for (Path part : home) {
                if (part.toString().equalsIgnoreCase(ToolLayout.FOLDER)) {
                    return;
                }
            }
            if (ToolLayout.launcher(tool, home) != null && found.stream().noneMatch(existing -> same(existing, home))) {
                found.add(home);
            }
        } catch (RuntimeException ignored) {
            // Not a usable path (for example an entry with characters Windows paths can't hold).
        }
    }

    private static boolean same(Path left, Path right) {
        return left.toString().toLowerCase(Locale.ROOT).equals(right.toString().toLowerCase(Locale.ROOT));
    }

    private static String expand(String entry, Map<String, String> variables) {
        Matcher matcher = VARIABLE.matcher(entry);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = value(variables, matcher.group(1)).orElse(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Optional<String> value(Map<String, String> variables, String name) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null && !entry.getValue().isBlank())
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static Optional<String> fromJarName(Path lib, String prefix) {
        if (!Files.isDirectory(lib)) {
            return Optional.empty();
        }
        try (Stream<Path> jars = Files.list(lib)) {
            return jars.map(jar -> jar.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".jar"))
                    .map(name -> name.substring(prefix.length(), name.length() - 4))
                    .filter(version -> !version.isEmpty() && Character.isDigit(version.charAt(0)))
                    .findFirst();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    /** Maven 2.0.x–2.2.x ship {@code lib\maven-<v>-uber.jar} instead of maven-core. */
    private static Optional<String> maven2Uber(Path lib) {
        return fromJarName(lib, "maven-")
                .filter(name -> name.endsWith("-uber"))
                .map(name -> name.substring(0, name.length() - "-uber".length()));
    }

    private static Optional<String> implementationVersion(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            return Optional.ofNullable(file.getManifest())
                    .map(manifest -> manifest.getMainAttributes().getValue("Implementation-Version"))
                    .map(String::trim)
                    .filter(version -> !version.isEmpty());
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static void copy(Path source, Path destination) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
