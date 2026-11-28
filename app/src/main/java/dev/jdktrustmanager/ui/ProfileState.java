package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.profiles.ProfileMatch;
import dev.jdktrustmanager.profiles.ProfileResolver;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything a refresh reads, gathered off the FX thread. */
record ProfileState(List<Profile> profiles, String error, Optional<String> active, Map<String, List<String>> missing,
        List<Installation> managed, Map<Tool, List<String>> installed, Map<String, String> userVariables, String userPath) {

    static ProfileState empty() {
        return new ProfileState(List.of(), null, Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), "");
    }

    static ProfileState read(ToolContext context, ProfileStore store) {
        Path root = context.root();
        Map<String, String> user = Map.of();
        try {
            user = RegistryEnvironment.readAll(RegistryEnvironment.USER).orElse(Map.of());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        Map<Tool, List<String>> installed = new EnumMap<>(Tool.class);
        for (BuildTool tool : BuildTools.ALL) {
            try {
                installed.put(tool.tool(), ToolLayout.installed(root, tool).stream().map(home -> ToolLayout.versionOf(tool, home)).toList());
            } catch (IOException ex) {
                installed.put(tool.tool(), List.of());
            }
        }
        List<Installation> managed = context.managedJdks();
        String userPath = JavaVariables.find(user, "Path");
        try {
            List<Profile> profiles = store.read();
            Map<String, List<String>> missing = new HashMap<>();
            for (Profile profile : profiles) {
                missing.put(profile.name(), ProfileActivator.labels(ProfileResolver.resolve(profile, root).missing()));
            }
            return new ProfileState(profiles, null, ProfileMatch.active(profiles, user, root), missing, managed, installed, user, userPath);
        } catch (IOException | RuntimeException ex) {
            return new ProfileState(List.of(), ex.getMessage(), Optional.empty(), Map.of(), managed, installed, user, userPath);
        }
    }
}
