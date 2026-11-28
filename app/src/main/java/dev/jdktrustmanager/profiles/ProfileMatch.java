package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Profile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which profile the user environment is in right now. Never stored: a one-off switch on another tab simply means no
 * profile matches.
 */
public final class ProfileMatch {

    private ProfileMatch() {
    }

    /** The first profile, in list order, whose JDK and tools are exactly what the user variables point at. */
    public static Optional<String> active(List<Profile> profiles, Map<String, String> userVariables, Path root) {
        for (Profile profile : profiles) {
            if (matches(profile, userVariables, root)) {
                return Optional.of(profile.name());
            }
        }
        return Optional.empty();
    }

    static boolean matches(Profile profile, Map<String, String> userVariables, Path root) {
        ProfileResolver.Resolved resolved = ProfileResolver.resolve(profile, root);
        if (!Installations.samePath(value(userVariables, "JAVA_HOME"), resolved.jdk().folder().toString())) {
            return false;
        }
        for (BuildTool tool : BuildTools.ALL) {
            String home = value(userVariables, tool.homeVariables().get(0));
            ProfileResolver.Slot slot = resolved.tools().get(tool.tool());
            if (slot == null ? !home.isBlank() : !Installations.samePath(home, slot.folder().toString())) {
                return false;
            }
        }
        return true;
    }

    private static String value(Map<String, String> variables, String name) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null)
                .map(entry -> entry.getValue().trim())
                .findFirst()
                .orElse("");
    }
}
