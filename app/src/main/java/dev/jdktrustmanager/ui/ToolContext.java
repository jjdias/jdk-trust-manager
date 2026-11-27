package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.JdkPicker;
import dev.jdktrustmanager.buildtools.ToolAdopter;
import dev.jdktrustmanager.buildtools.ToolCatalog;
import dev.jdktrustmanager.buildtools.ToolInstaller;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolInstallations;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** What the build tool screens share: state files, the work queue, and the installer, catalog, and adopter. */
record ToolContext(Path root, Path toolsFile, JsonStores stores, WorkQueue queue, ToolCatalog catalog, ToolInstaller installer,
        ToolAdopter adopter, Supplier<Settings> settings, Path installationsFile) {

    /**
     * That tool's rows, newest first, with rows whose folder is gone dropped and "on PATH" taken from the registry
     * (the user's MAVEN_HOME/ANT_HOME/GRADLE_HOME), which is what a new terminal will see.
     */
    synchronized List<ToolInstallation> tools(BuildTool tool) throws IOException, InterruptedException {
        String home = RegistryEnvironment.query(RegistryEnvironment.USER, tool.homeVariables().get(0));
        String onPath = home == null || home.isBlank() ? null : home;
        List<ToolInstallation> all = dev.jdktrustmanager.buildtools.ToolInventory.reconcile(
                stores.readTools(toolsFile), tool, dev.jdktrustmanager.buildtools.ToolLayout.installed(root, tool), onPath);
        stores.writeTools(toolsFile, all);
        return ToolInstallations.of(all, tool.tool());
    }

    /** Replaces the row with the same path, or adds it. */
    synchronized void save(ToolInstallation row) throws IOException {
        List<ToolInstallation> all = new ArrayList<>(stores.readTools(toolsFile));
        all.removeIf(item -> Installations.samePath(item.path(), row.path()));
        all.add(row);
        stores.writeTools(toolsFile, all);
    }

    synchronized void remove(ToolInstallation row) throws IOException {
        List<ToolInstallation> all = new ArrayList<>(stores.readTools(toolsFile));
        all.removeIf(item -> Installations.samePath(item.path(), row.path()));
        stores.writeTools(toolsFile, all);
    }

    synchronized void update(java.util.function.UnaryOperator<List<ToolInstallation>> change) throws IOException {
        stores.writeTools(toolsFile, change.apply(new ArrayList<>(stores.readTools(toolsFile))));
    }

    List<Installation> managedJdks() {
        try {
            return Files.exists(installationsFile) ? stores.readInstallations(installationsFile) : List.of();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** The user JAVA_HOME (falling back to the machine one), or empty. */
    String activeJavaHome() {
        try {
            String user = RegistryEnvironment.query(RegistryEnvironment.USER, "JAVA_HOME");
            if (user != null && !user.isBlank()) {
                return user;
            }
            String machine = RegistryEnvironment.query(RegistryEnvironment.MACHINE, "JAVA_HOME");
            return machine == null ? "" : machine;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    /** A sentence when the active JDK is outside {@code tool version}'s Java range, otherwise null. */
    String javaWarning(BuildTool tool, String version) {
        var range = tool.javaRange(version);
        String home = activeJavaHome();
        Optional<Integer> major = home.isBlank() ? Optional.empty() : JdkPicker.major(Path.of(home));
        if (major.isEmpty()) {
            return tool.displayName() + " " + version + " needs " + range.describe() + ", and JAVA_HOME isn't set to a JDK.";
        }
        if (range.allows(major.get())) {
            return null;
        }
        return tool.displayName() + " " + version + " needs " + range.describe() + "; JAVA_HOME is Java " + major.get() + ".";
    }

    /** User and machine variables merged, user winning, for adopt scans. */
    static Map<String, String> variables() throws IOException, InterruptedException {
        Map<String, String> merged = new java.util.LinkedHashMap<>(RegistryEnvironment.queryAll(RegistryEnvironment.MACHINE));
        merged.putAll(RegistryEnvironment.queryAll(RegistryEnvironment.USER));
        return merged;
    }

    /** User Path then machine Path. */
    static String combinedPath(Map<String, String> user, Map<String, String> machine) {
        return JavaVariables.find(user, "Path") + ";" + JavaVariables.find(machine, "Path");
    }
}
