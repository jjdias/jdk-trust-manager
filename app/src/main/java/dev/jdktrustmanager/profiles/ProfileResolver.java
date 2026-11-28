package dev.jdktrustmanager.profiles;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.install.Layout;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out where a profile's JDK and tools live under the install root and whether each is installed. "Installed"
 * means the folder holds a JDK home or the tool's launcher, whatever the state files say.
 */
public final class ProfileResolver {

    /** One slot of a profile: a label such as "temurin 8" or "Maven 3.3.9", its folder, and whether it's there. */
    public record Slot(String label, Path folder, boolean installed) {
    }

    /** {@code tools} holds only the tools in the profile, in Maven, Ant, Gradle order. */
    public record Resolved(Profile profile, Slot jdk, Map<Tool, Slot> tools, List<Slot> missing) {
    }

    private ProfileResolver() {
    }

    public static Resolved resolve(Profile profile, Path root) {
        Path jdkFolder = Layout.destination(root, profile.jdk().vendorSlug(), profile.jdk().major());
        Slot jdk = new Slot(jdkLabel(profile), jdkFolder, JdkScanner.isHome(jdkFolder));
        Map<Tool, Slot> tools = new LinkedHashMap<>();
        List<Slot> missing = new ArrayList<>();
        if (!jdk.installed()) {
            missing.add(jdk);
        }
        for (BuildTool tool : BuildTools.ALL) {
            String version = profile.version(tool.tool());
            if (version == null || version.isBlank()) {
                continue;
            }
            Path folder = ToolLayout.destination(root, tool, version);
            Slot slot = new Slot(tool.displayName() + " " + version, folder, ToolLayout.launcher(tool, folder) != null);
            tools.put(tool.tool(), slot);
            if (!slot.installed()) {
                missing.add(slot);
            }
        }
        return new Resolved(profile, jdk, tools, List.copyOf(missing));
    }

    /** "temurin 8 · Maven 3.3.9 · Ant 1.9.16". */
    public static String describe(Profile profile) {
        StringBuilder text = new StringBuilder(jdkLabel(profile));
        for (BuildTool tool : BuildTools.ALL) {
            String version = profile.version(tool.tool());
            if (version != null && !version.isBlank()) {
                text.append(" · ").append(tool.displayName()).append(' ').append(version);
            }
        }
        return text.toString();
    }

    private static String jdkLabel(Profile profile) {
        return profile.jdk().vendorSlug() + " " + profile.jdk().major();
    }
}
