package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolInstallations;
import dev.jdktrustmanager.model.ToolSource;
import dev.jdktrustmanager.model.Verification;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Keeps tools.json in step with the version folders on disk, so nothing installed is ever unmanageable. */
public final class ToolInventory {

    private ToolInventory() {
    }

    /**
     * {@code all} with {@code tool}'s rows matched to {@code onDisk}: rows whose folder is gone are dropped, folders
     * without a row (an install interrupted before it was recorded) are added as {@link Verification#UNRECORDED},
     * and "on PATH" is set from {@code onPathHome}. Other tools' rows are untouched.
     */
    public static List<ToolInstallation> reconcile(List<ToolInstallation> all, BuildTool tool, List<Path> onDisk, String onPathHome) {
        List<ToolInstallation> next = new ArrayList<>();
        for (ToolInstallation row : all) {
            if (row.tool() != tool.tool() || onDisk.stream().anyMatch(home -> Installations.samePath(home.toString(), row.path()))) {
                next.add(row);
            }
        }
        for (Path home : onDisk) {
            boolean recorded = next.stream()
                    .anyMatch(row -> row.tool() == tool.tool() && Installations.samePath(row.path(), home.toString()));
            if (!recorded) {
                next.add(new ToolInstallation(tool.tool(), ToolLayout.versionOf(tool, home), home.toString(), ToolSource.DOWNLOADED,
                        Verification.UNRECORDED, null, false, false, ""));
            }
        }
        return ToolInstallations.markActive(next, tool.tool(), onPathHome);
    }
}
