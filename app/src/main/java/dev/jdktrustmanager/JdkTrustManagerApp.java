package dev.jdktrustmanager;

import dev.jdktrustmanager.catalog.DiscoCatalog;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.EnvironmentScope;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.TrustSchedule;
import dev.jdktrustmanager.ui.ActivityPane;
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
                : new Settings("C:\\Java", EnvironmentScope.USER, 24, true, false, false, false, false, false);
        AtomicReference<Settings> current = new AtomicReference<>(settings);
        DiscoClient client = DiscoClient.live();
        WorkQueue queue = new WorkQueue(AppFiles.logs());
        CatalogPane catalog = new CatalogPane(client, new DiscoCatalog(client, stores, AppFiles.catalog()), queue, current::get);
        Path runtimeHome = Path.of(System.getProperty("java.home"));
        Path managedRoot = Path.of(settings.rootDirectory());
        Path bundledRuntime = runtimeHome.startsWith(managedRoot) ? null : runtimeHome;
        InstalledPane installed = new InstalledPane(managedRoot, AppFiles.installations(), stores, queue, bundledRuntime);
        TruststorePane truststore = new TruststorePane();
        TrustedCertsPane trusted = new TrustedCertsPane(managedRoot, AppFiles.installations(), bundledRuntime, queue);
        Path lastSync = managedRoot.resolve("trusted-certs").resolve("last-sync");
        if (TrustSchedule.due(lastSync, settings.pauseAutomaticTrustSync(), Instant.now())) {
            queue.submit(TrustedCertsPane.syncJob(managedRoot, AppFiles.installations(), bundledRuntime, null));
        }
        ActivityPane activity = new ActivityPane(AppFiles.logs(), queue);
        EnvironmentPane environment = new EnvironmentPane(settings.rootDirectory(), AppFiles.installations(), stores);
        SettingsPane preferences = new SettingsPane(settings, AppFiles.settings(), stores, current::set);
        BorderPane shell = new BorderPane(new MainView(catalog, installed, environment, truststore, trusted, activity));
        shell.setTop(preferences);
        shell.setBottom(new JobProgressPane(queue));
        stage.setTitle("JDK Trust Manager");
        stage.setScene(new Scene(shell, 980, 760));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
