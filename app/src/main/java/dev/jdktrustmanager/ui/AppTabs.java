package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.HttpToolHttp;
import dev.jdktrustmanager.buildtools.ToolAdopter;
import dev.jdktrustmanager.buildtools.ToolCatalog;
import dev.jdktrustmanager.buildtools.ToolHttp;
import dev.jdktrustmanager.buildtools.ToolInstaller;
import dev.jdktrustmanager.buildtools.ToolVerifier;
import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/** Top level: Java (its five screens), Maven, Ant, Gradle, Profiles, then Activity, which logs every tab's jobs. */
public final class AppTabs extends TabPane {

    private final ProfileActivator activator;
    private final ProfileSwitcher switcher;

    public AppTabs(Node java, List<ToolTab> tools, ProfilesPane profiles, Node activity, ProfileActivator activator,
            ProfileSwitcher switcher) {
        this.activator = activator;
        this.switcher = switcher;
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getTabs().add(new Tab(Screens.TOP_LEVEL.get(0), java));
        for (ToolTab tab : tools) {
            getTabs().add(new Tab(tab.tool().displayName(), tab));
        }
        Tab profilesTab = new Tab(Screens.TOP_LEVEL.get(Screens.TOP_LEVEL.size() - 2), profiles);
        profilesTab.setOnSelectionChanged(event -> {
            if (profilesTab.isSelected()) {
                profiles.refresh();
            }
        });
        getTabs().add(profilesTab);
        getTabs().add(new Tab(Screens.TOP_LEVEL.get(Screens.TOP_LEVEL.size() - 1), activity));
    }

    /** Every tab after Java, wired to the real network, state files, and work queue, sharing one tool context. */
    public static AppTabs create(Node java, Node activity, Path root, JsonStores stores, WorkQueue queue, Supplier<Settings> settings,
            CatalogBrowser browser, DiscoClient client) {
        ToolContext context = context(root, stores, queue, settings);
        ProfileStore profileStore = new ProfileStore(stores, AppFiles.profiles());
        ProfileActivator activator = new ProfileActivator(context, profileStore, browser, client);
        queue.subscribe(event -> {
            if (event.state() == WorkQueue.State.DONE || event.state() == WorkQueue.State.FAILED) {
                activator.changed();
            }
        });
        return new AppTabs(java, toolTabs(context), new ProfilesPane(context, profileStore, activator), activity, activator,
                new ProfileSwitcher(activator, context, profileStore));
    }

    /** The top-bar profile drop-down; the app adds it beside Options. */
    public ProfileSwitcher switcher() {
        return switcher;
    }

    /** Tells the Profiles tab and the switcher to re-read, for example when the window regains focus. */
    public void refreshProfiles() {
        activator.changed();
    }

    static ToolContext context(Path root, JsonStores stores, WorkQueue queue, Supplier<Settings> settings) {
        ToolHttp http = new HttpToolHttp();
        return new ToolContext(root, AppFiles.tools(), stores, queue,
                new ToolCatalog(http, stores, AppFiles.toolCatalog()),
                new ToolInstaller(new ToolVerifier(http, AppFiles.keysDir()), AppFiles.zipCache()),
                new ToolAdopter(), settings, AppFiles.installations());
    }

    static List<ToolTab> toolTabs(ToolContext context) {
        return BuildTools.ALL.stream().map(tool -> new ToolTab(tool, context)).toList();
    }
}
