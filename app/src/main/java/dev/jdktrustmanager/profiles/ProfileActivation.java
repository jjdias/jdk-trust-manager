package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.JavaRange;
import dev.jdktrustmanager.model.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What activating a profile will change, what to warn about, and whether it's safe to write yet. */
public final class ProfileActivation {

    private ProfileActivation() {
    }

    /** "temurin 8 → JAVA_HOME", "Maven 3.3.9 → on PATH", "Gradle → off PATH", in JDK, Maven, Ant, Gradle order. */
    public static List<String> changes(ProfileResolver.Resolved resolved) {
        List<String> lines = new ArrayList<>();
        lines.add(resolved.jdk().label() + " → JAVA_HOME");
        for (BuildTool tool : BuildTools.ALL) {
            ProfileResolver.Slot slot = resolved.tools().get(tool.tool());
            lines.add(slot == null ? tool.displayName() + " → off PATH" : slot.label() + " → on PATH");
        }
        return lines;
    }

    /** One sentence for each tool whose Java range doesn't include the profile's JDK. */
    public static List<String> javaWarnings(Profile profile) {
        List<String> warnings = new ArrayList<>();
        int major = profile.jdk().major();
        for (BuildTool tool : BuildTools.ALL) {
            String version = profile.version(tool.tool());
            if (version == null || version.isBlank()) {
                continue;
            }
            JavaRange range = tool.javaRange(version);
            if (!range.allows(major)) {
                warnings.add(tool.displayName() + " " + version + " needs " + range.describe() + "; this profile's JDK is Java " + major + ".");
            }
        }
        return warnings;
    }

    /**
     * Empty when every slot is installed. Otherwise the reason nothing will be written. The activation job checks this
     * again after any installs it waited for, so a failed or cancelled install never leads to a partial switch.
     */
    public static Optional<String> readyToWrite(ProfileResolver.Resolved resolvedAgain) {
        List<String> missing = resolvedAgain.missing().stream().map(ProfileResolver.Slot::label).toList();
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Not activated: " + join(missing) + (missing.size() == 1 ? " isn't installed." : " aren't installed."));
    }

    /** "a", "a and b", "a, b and c". */
    public static String join(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }
}
