package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoCatalog;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.install.ZipInstaller;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public final class CatalogPane extends BorderPane {

    private final CatalogBrowser browser;
    private final DiscoClient client;
    private final WorkQueue queue;
    private final ZipInstaller installer = new ZipInstaller();
    private final Supplier<Settings> settings;
    private final JsonStores stores = new JsonStores();
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-catalog");
        thread.setDaemon(true);
        return thread;
    });
    private final Label detail = new Label("Select a major version.");
    private final java.util.List<VendorVersions> vendors = new ArrayList<>();
    private CatalogBrowser.Card selected;

    public CatalogPane(DiscoClient client, DiscoCatalog catalog, WorkQueue queue, Supplier<Settings> settings) {
        this.client = client;
        this.browser = new CatalogBrowser(catalog);
        this.queue = queue;
        this.settings = settings;
        TreeView<VersionNode> tree = new TreeView<>();
        tree.setShowRoot(false);
        TreeItem<VersionNode> root = new TreeItem<>(new VersionNode("Vendors", null, -1));
        root.setExpanded(true);
        tree.setRoot(root);
        TextField filter = new TextField();
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showVendors(root, text));
        Button install = new Button("Install");
        install.setDisable(true);
        install.setOnAction(event -> queueInstall());
        setTop(filter);
        setCenter(tree);
        setRight(new VBox(8, detail, install));
        setPadding(new Insets(8));
        loadVendors(root, tree, install);
    }

    private void loadVendors(TreeItem<VersionNode> root, TreeView<VersionNode> tree, Button install) {
        lookup.execute(() -> {
            try {
                for (Distribution distribution : browser.vendors(settings.get(), false)) {
                    java.util.List<Integer> majors = new ArrayList<>(browser.majors(distribution, settings.get()).keySet());
                    javafx.application.Platform.runLater(() -> {
                        vendors.add(new VendorVersions(distribution, majors));
                        showVendors(root, ((TextField) getTop()).getText());
                    });
                }
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> detail.setText(ex.getMessage()));
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item == null || item.getValue() == null || item.getValue().major() < 0 || item.getValue().distribution() == null) {
                return;
            }
            VersionNode node = item.getValue();
            lookup.execute(() -> {
                try {
                    Settings current = settings.get();
                    selected = browser.latest(node.distribution(), node.major(), current, current.showJavafxBuilds(), false);
                    javafx.application.Platform.runLater(() -> show(install));
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> detail.setText(ex.getMessage()));
                }
            });
        });
    }

    private void showVendors(TreeItem<VersionNode> root, String query) {
        root.getChildren().clear();
        boolean flat = query != null && !query.isBlank();
        for (VendorVersions vendor : vendors) {
            if (!flat) {
                TreeItem<VersionNode> parent = new TreeItem<>(new VersionNode(vendor.distribution().name(), vendor.distribution(), -1));
                for (int major : vendor.majors()) {
                    parent.getChildren().add(new TreeItem<>(new VersionNode(Integer.toString(major), vendor.distribution(), major)));
                }
                root.getChildren().add(parent);
                continue;
            }
            for (int major : vendor.majors()) {
                String label = vendor.distribution().name() + " " + major;
                if (TextFilter.matches(label, query)) {
                    root.getChildren().add(new TreeItem<>(new VersionNode(label, vendor.distribution(), major)));
                }
            }
        }
    }

    private record VendorVersions(Distribution distribution, java.util.List<Integer> majors) {
    }

    private record VersionNode(String label, Distribution distribution, int major) {
        @Override
        public String toString() {
            return label;
        }
    }

    private void show(Button install) {
        CatalogPackage pkg = selected.pkg();
        if (pkg == null) {
            detail.setText(selected.vendor() + " " + selected.major() + (selected.stale() ? " (stale cache)" : " has no package"));
            install.setDisable(true);
            return;
        }
        detail.setText(selected.vendor()
                + (pkg.javafxBundled() ? " JavaFX" : "")
                + "\n" + pkg.javaVersion()
                + "\ntck " + pkg.tckTested()
                + "\n" + pkg.termOfSupport()
                + "\n" + pkg.architecture()
                + "\n" + pkg.size()
                + "\n" + (selected.installed() ? "installed" : "not installed")
                + (selected.stale() ? "\nstale cache" : ""));
        install.setDisable(false);
    }

    private void queueInstall() {
        CatalogBrowser.Card card = selected;
        if (card == null || card.pkg() == null) {
            return;
        }
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.INSTALL;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                progress.log("Install " + card.vendor() + " " + card.major() + " " + card.pkg().javaVersion());
                progress.state(WorkQueue.State.DOWNLOADING);
                if (progress.cancelled()) {
                    progress.log("Download cancelled");
                    return;
                }
                var packageDetail = client.packageDetail(card.pkg().id());
                Path root = Path.of(settings.get().rootDirectory());
                Path runtimeHome = Path.of(System.getProperty("java.home"));
                Path bundled = runtimeHome.startsWith(root) ? null : runtimeHome;
                var installed = installer.install(card.pkg(), packageDetail, root, AppFiles.zipCache(), bundled, home -> {
                    progress.state(WorkQueue.State.UPDATING_TRUSTSTORE);
                    progress.log("Replaying trusted certificates");
                    try {
                        new dev.jdktrustmanager.trust.TrustSync().syncHomes(root, java.util.List.of(home), bundled, progress::log);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new java.io.IOException(ex);
                    }
                }, new ZipInstaller.InstallProgress() {
                    @Override
                    public void log(String line) {
                        progress.log(line);
                    }

                    @Override
                    public void bytes(long read, long total) {
                        progress.bytes(read, total);
                    }

                    @Override
                    public void phase(String phase) {
                        progress.log(phase);
                        if (phase.startsWith("Verifying")) {
                            progress.state(WorkQueue.State.VERIFYING);
                        }
                        if (phase.startsWith("Unpacking")) {
                            queue.markUnzipStarted();
                            progress.state(WorkQueue.State.UNPACKING);
                        }
                    }
                });
                remember(installed);
                progress.log("Installed " + installed.path());
            }
        });
    }

    private void remember(dev.jdktrustmanager.model.Installation installed) throws java.io.IOException {
        Path file = AppFiles.installations();
        List<dev.jdktrustmanager.model.Installation> known = Files.exists(file)
                ? new ArrayList<>(stores.readInstallations(file))
                : new ArrayList<>();
        known.removeIf(item -> item.path().equals(installed.path()));
        known.add(installed);
        stores.writeInstallations(file, known);
    }
}
