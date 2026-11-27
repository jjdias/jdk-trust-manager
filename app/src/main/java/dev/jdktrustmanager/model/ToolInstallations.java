package dev.jdktrustmanager.model;

import java.util.ArrayList;
import java.util.List;

public final class ToolInstallations {

    private ToolInstallations() {
    }

    /** Marks the row at {@code path} active for {@code tool}; other rows of that tool go inactive. Null clears the tool. */
    public static List<ToolInstallation> markActive(List<ToolInstallation> known, Tool tool, String path) {
        List<ToolInstallation> next = new ArrayList<>(known.size());
        for (ToolInstallation item : known) {
            if (item.tool() != tool) {
                next.add(item);
                continue;
            }
            boolean active = path != null && Installations.samePath(item.path(), path);
            next.add(item.active() == active ? item : copy(item, active, item.pinned()));
        }
        return next;
    }

    public static List<ToolInstallation> withPinned(List<ToolInstallation> known, String path, boolean pinned) {
        List<ToolInstallation> next = new ArrayList<>(known.size());
        for (ToolInstallation item : known) {
            next.add(Installations.samePath(item.path(), path) && item.pinned() != pinned
                    ? copy(item, item.active(), pinned)
                    : item);
        }
        return next;
    }

    /** That tool's rows, newest version first. */
    public static List<ToolInstallation> of(List<ToolInstallation> known, Tool tool) {
        return known.stream()
                .filter(item -> item.tool() == tool)
                .sorted((left, right) -> ToolVersions.NEWEST_FIRST.compare(left.version(), right.version()))
                .toList();
    }

    private static ToolInstallation copy(ToolInstallation item, boolean active, boolean pinned) {
        return new ToolInstallation(item.tool(), item.version(), item.path(), item.source(), item.verification(),
                item.installedAt(), active, pinned, item.notes());
    }
}
