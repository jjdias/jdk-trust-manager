package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoCatalog;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.CatalogPackage;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.JsonStores;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
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
    private final Supplier<Settings> settings;
    private final JsonStores stores = new JsonStores();
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-catalog");
        thread.setDaemon(true);
        return thread;
    });
    private final Label detail = new Label("Select a major version.");
    private final TextField filter = new TextField();
    private final java.util.List<VendorVersions> vendors = new ArrayList<>();
    private volatile CatalogBrowser.Card selected;
    private volatile InstallButton.State selectedState;
    private volatile String selectedInstalledVersion;
    private volatile java.nio.file.Path selectedHome;
    private final Button install = new Button("Install");

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
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showVendors(root, text));
        install.setDisable(true);
        install.setOnAction(event -> queueInstall());
        detail.setWrapText(true);
        detail.setMinWidth(220);
        detail.setMaxWidth(260);
        setTop(new VBox(Spacing.GAP, install, filter));
        setCenter(tree);
        setRight(detail);
        Spacing.page(this);
        loadVendors(root, tree, install);
    }

    private void loadVendors(TreeItem<VersionNode> root, TreeView<VersionNode> tree, Button install) {
        lookup.execute(() -> {
            try {
                for (Distribution distribution : browser.vendors(settings.get(), false)) {
                    java.util.List<Integer> majors = new ArrayList<>(browser.majors(distribution, settings.get()).keySet());
                    javafx.application.Platform.runLater(() -> {
                        vendors.add(new VendorVersions(distribution, majors));
                        showVendors(root, filter.getText());
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
                    CatalogBrowser.Card card = browser.latest(node.distribution(), node.major(), current, current.showJavafxBuilds(), false);
                    String installedVersion = null;
                    java.nio.file.Path home = null;
                    if (card.pkg() != null) {
                        home = dev.jdktrustmanager.install.Layout.destination(java.nio.file.Path.of(current.rootDirectory()),
                                dev.jdktrustmanager.catalog.Slug.of(card.pkg().distribution(), card.pkg().javafxBundled(), card.pkg().architecture()),
                                card.pkg().major());
                        installedVersion = dev.jdktrustmanager.install.ZipInstaller.installedVersion(home);
                    }
                    selected = card;
                    selectedHome = home;
                    selectedInstalledVersion = installedVersion;
                    selectedState = card.pkg() == null ? null : InstallButton.state(installedVersion, card.pkg().javaVersion());
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
                + "\n" + (selectedState.update() ? selectedInstalledVersion + " is installed; Update replaces it"
                        : !selectedState.enabled() ? selectedInstalledVersion + " is installed" : "not installed")
                + (selected.stale() ? "\nstale cache" : ""));
        install.setText(selectedState.label());
        install.setDisable(!selectedState.enabled());
    }

    private void queueInstall() {
        CatalogBrowser.Card card = selected;
        InstallButton.State state = selectedState;
        if (card == null || card.pkg() == null || state == null || !state.enabled()) {
            return;
        }
        if (state.update() && !ConfirmDialog.confirm(this,
                Confirmations.update(String.valueOf(selectedHome), selectedInstalledVersion, card.pkg().javaVersion()))) {
            return;
        }
        install.setDisable(true);
        queue.submit(new InstallJob(card, client, settings, queue, stores, () -> {
            install.setText("Installed");
        }));
    }
}
