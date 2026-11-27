package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.HttpToolHttp;
import dev.jdktrustmanager.buildtools.ToolAdopter;
import dev.jdktrustmanager.buildtools.ToolCatalog;
import dev.jdktrustmanager.buildtools.ToolHttp;
import dev.jdktrustmanager.buildtools.ToolInstaller;
import dev.jdktrustmanager.buildtools.ToolVerifier;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** Top level: Java (its five screens), Maven, Ant, Gradle, then Activity, which logs every tab's jobs. */
public final class AppTabs extends TabPane {

    public AppTabs(Node java, List<ToolTab> tools, Node activity) {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getTabs().add(new Tab(Screens.TOP_LEVEL.get(0), java));
        for (ToolTab tab : tools) {
            getTabs().add(new Tab(tab.tool().displayName(), tab));
        }
        getTabs().add(new Tab(Screens.TOP_LEVEL.get(Screens.TOP_LEVEL.size() - 1), activity));
    }

    /** The three tool tabs wired to the real network, state files, and work queue. */
    public static List<ToolTab> toolTabs(Path root, JsonStores stores, WorkQueue queue, Supplier<Settings> settings) {
        ToolHttp http = new HttpToolHttp();
        ToolContext context = new ToolContext(root, AppFiles.tools(), stores, queue,
                new ToolCatalog(http, stores, AppFiles.toolCatalog()),
                new ToolInstaller(new ToolVerifier(http, AppFiles.keysDir()), AppFiles.zipCache()),
                new ToolAdopter(), settings, AppFiles.installations());
        return BuildTools.ALL.stream().map(tool -> new ToolTab(tool, context)).toList();
    }
}
