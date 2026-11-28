package dev.jdktrustmanager;

import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoCatalog;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.EnvironmentScope;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.TrustSchedule;
import dev.jdktrustmanager.ui.ActivityPane;
import dev.jdktrustmanager.ui.AppTabs;
import dev.jdktrustmanager.ui.CatalogPane;
import dev.jdktrustmanager.ui.EnvironmentPane;
import dev.jdktrustmanager.ui.InstalledPane;
import dev.jdktrustmanager.ui.JobProgressPane;
import dev.jdktrustmanager.ui.MainView;
import dev.jdktrustmanager.ui.TrustedCertsPane;
import dev.jdktrustmanager.ui.TruststorePane;
import dev.jdktrustmanager.ui.SettingsPane;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class JdkTrustManagerApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        JsonStores stores = new JsonStores();
        Settings settings = Files.exists(AppFiles.settings())
                ? stores.readSettings(AppFiles.settings())
                : new Settings("C:\\Java", EnvironmentScope.USER, 24, true, false, false, false, false, false, false);
        AtomicReference<Settings> current = new AtomicReference<>(settings);
        DiscoClient client = DiscoClient.live();
        WorkQueue queue = new WorkQueue(AppFiles.logs());
        DiscoCatalog discoCatalog = new DiscoCatalog(client, stores, AppFiles.catalog());
        CatalogPane catalog = new CatalogPane(client, discoCatalog, queue, current::get);
        Path runtimeHome = Path.of(System.getProperty("java.home"));
        Path managedRoot = Path.of(settings.rootDirectory());
        Path bundledRuntime = runtimeHome.startsWith(managedRoot) ? null : runtimeHome;
        InstalledPane installed = new InstalledPane(managedRoot, AppFiles.installations(), stores, queue, bundledRuntime,
                new CatalogBrowser(discoCatalog), client, current::get);
        TruststorePane truststore = new TruststorePane(AppFiles.installations(), stores, getHostServices()::showDocument);
        TrustedCertsPane trusted = new TrustedCertsPane(managedRoot, AppFiles.installations(), bundledRuntime, queue);
        Path lastSync = managedRoot.resolve("trusted-certs").resolve("last-sync");
        if (TrustSchedule.due(lastSync, settings.pauseAutomaticTrustSync(), Instant.now())) {
            queue.submit(TrustedCertsPane.syncJob(managedRoot, AppFiles.installations(), bundledRuntime, null));
        }
        ActivityPane activity = new ActivityPane(AppFiles.logs(), queue);
        EnvironmentPane environment = new EnvironmentPane(settings.rootDirectory(), AppFiles.installations(), stores);
        SettingsPane preferences = new SettingsPane(settings, AppFiles.settings(), stores, current::set);
        MainView java = new MainView(catalog, installed, environment, truststore, trusted);
        AppTabs tabs = AppTabs.create(java, activity, managedRoot, stores, queue, current::get,
                new CatalogBrowser(discoCatalog), client);
        preferences.getChildren().add(tabs.switcher());
        BorderPane shell = new BorderPane(tabs);
        shell.setTop(preferences);
        shell.setBottom(new JobProgressPane(queue));
        stage.setTitle("JDK Trust Manager");
        stage.setScene(new Scene(shell, 980, 760));
        stage.focusedProperty().addListener((observable, was, focused) -> {
            if (focused) {
                tabs.refreshProfiles();
            }
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
