package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** One build tool's screens: Versions, Installed, Environment. A change on one refreshes all three. */
public final class ToolTab extends TabPane {

    private final BuildTool tool;

    public ToolTab(BuildTool tool, ToolContext context) {
        this.tool = tool;
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        ToolVersionsPane[] versions = new ToolVersionsPane[1];
        ToolInstalledPane[] installed = new ToolInstalledPane[1];
        ToolEnvironmentPane[] environment = new ToolEnvironmentPane[1];
        Runnable changed = () -> {
            versions[0].refresh();
            installed[0].refresh();
            environment[0].refresh();
        };
        versions[0] = new ToolVersionsPane(tool, context, changed);
        installed[0] = new ToolInstalledPane(tool, context, changed);
        environment[0] = new ToolEnvironmentPane(tool, context, changed);
        getTabs().addAll(
                new Tab(Screens.TOOL.get(0), versions[0]),
                new Tab(Screens.TOOL.get(1), installed[0]),
                new Tab(Screens.TOOL.get(2), environment[0]));
    }

    public BuildTool tool() {
        return tool;
    }
}
