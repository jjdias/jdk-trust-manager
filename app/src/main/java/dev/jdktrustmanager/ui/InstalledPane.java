package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.catalog.Slug;
import dev.jdktrustmanager.env.PathEditor;
import dev.jdktrustmanager.env.PathUsage;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.UserEnvironment;
import dev.jdktrustmanager.install.AdoptPlan;
import dev.jdktrustmanager.install.Adopter;
import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.install.Uninstaller;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.TrustSync;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class InstalledPane extends BorderPane {

    /** One line in the list: a JDK home, and its record when the app manages it. */
    private record Row(Path path, Installation installation, CatalogBrowser.Card update) {
        boolean managed() {
            return installation != null;
        }

        boolean pinned() {
            return installation != null && installation.pinned();
        }

        @Override
        public String toString() {
            if (installation == null) {
                return "not managed   " + path;
            }
            StringBuilder text = new StringBuilder();
            text.append(installation.vendorSlug()).append(' ').append(installation.javaVersion()).append("   ").append(path);
            if (installation.active()) {
                text.append("   [JAVA_HOME]");
            }
            if (installation.pinned()) {
                text.append("   [pinned]");
            }
            if (update != null) {
                text.append("   [update ").append(update.pkg().javaVersion()).append(" available]");
            }
            return text.toString();
        }
    }

    private final Path root;
    private final Path installationsFile;
    private final JsonStores stores;
    private final WorkQueue queue;
    private final Path bundledRuntime;
    private final CatalogBrowser browser;
    private final DiscoClient client;
    private final Supplier<Settings> settings;
    private final List<Row> rowsData = new ArrayList<>();
    private final Map<String, CatalogBrowser.Card> updates = new ConcurrentHashMap<>();
    private final ListView<Row> rows = new ListView<>();
    private final TextField filter = new TextField();
    private final Label status = new Label();
    private final Button setToPath = new Button("Set to path");
    private final Button adopt = new Button("Adopt");
    private final Button pin = new Button("Pin");
    private final Button checkUpdates = new Button("Check for updates");
    private final Button update = new Button("Update");
    private final Button remove = new Button("Remove");
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-updates");
        thread.setDaemon(true);
        return thread;
    });

    public InstalledPane(Path root, Path installationsFile, JsonStores stores, WorkQueue queue, Path bundledRuntime,
            CatalogBrowser browser, DiscoClient client, Supplier<Settings> settings) {
        this.root = root;
        this.installationsFile = installationsFile;
        this.stores = stores;
        this.queue = queue;
        this.bundledRuntime = bundledRuntime;
        this.browser = browser;
        this.client = client;
        this.settings = settings;
        Button refresh = new Button("Refresh");
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showRows());
        status.setWrapText(true);
        rows.setPlaceholder(new Label("No JDKs found. Install one from the Catalog."));
        rows.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> enableFor(row));
        refresh.setOnAction(event -> refresh());
        setToPath.setOnAction(event -> selected().ifPresent(this::setToPath));
        adopt.setOnAction(event -> selected().ifPresent(this::adopt));
        pin.setOnAction(event -> selected().ifPresent(this::togglePin));
        checkUpdates.setOnAction(event -> checkUpdates());
        update.setOnAction(event -> selected().ifPresent(this::update));
        remove.setOnAction(event -> selected().ifPresent(this::remove));
        HBox actions = new HBox(Spacing.GAP, refresh, setToPath, adopt, pin, checkUpdates, update, remove);
        actions.setAlignment(Pos.CENTER_LEFT);
        setTop(new VBox(Spacing.GAP, filter, actions, status));
        setCenter(rows);
        Spacing.page(this);
        enableFor(null);
        refresh();
    }

    private java.util.Optional<Row> selected() {
        Row row = rows.getSelectionModel().getSelectedItem();
        if (row == null) {
            status.setText("Select a version");
        }
        return java.util.Optional.ofNullable(row);
    }

    private void enableFor(Row row) {
        setToPath.setDisable(row == null);
        adopt.setDisable(row == null || row.managed());
        pin.setDisable(row == null || !row.managed());
        pin.setText(row != null && row.pinned() ? "Unpin" : "Pin");
        update.setDisable(row == null || row.update() == null || row.pinned());
        remove.setDisable(row == null || !row.managed() || row.pinned());
    }

    private void refresh() {
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.ADOPT;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                List<Installation> known = known();
                List<JdkScanner.Discovered> found = new JdkScanner().scan(root, known, List.of());
                List<Row> next = new ArrayList<>();
                for (JdkScanner.Discovered discovered : found) {
                    Installation installation = known.stream()
                            .filter(item -> Installations.samePath(item.path(), discovered.path().toString()))
                            .findFirst()
                            .orElse(null);
                    next.add(new Row(discovered.path(), installation,
                            installation == null ? null : updates.get(key(installation.path()))));
                }
                Platform.runLater(() -> {
                    Row before = rows.getSelectionModel().getSelectedItem();
                    rowsData.clear();
                    rowsData.addAll(next);
                    showRows();
                    if (before != null) {
                        rows.getItems().stream()
                                .filter(row -> row.path().equals(before.path()))
                                .findFirst()
                                .ifPresent(row -> rows.getSelectionModel().select(row));
                    }
                    enableFor(rows.getSelectionModel().getSelectedItem());
                });
            }
        });
    }

    private void showRows() {
        rows.getItems().setAll(rowsData.stream().filter(row -> TextFilter.matches(row.toString(), filter.getText())).toList());
    }

    private void setToPath(Row row) {
        String path = row.path().toString();
        if (!ConfirmDialog.confirm(this, Confirmations.setJavaHome(path, false))) {
            return;
        }
        submit(WorkQueue.Kind.ADOPT, progress -> {
            dev.jdktrustmanager.env.UserPathWriter.live()
                    .write(current -> dev.jdktrustmanager.env.EnvironmentPlan.forJava(current, root.toString(), path, false));
            if (Files.exists(installationsFile)) {
                stores.writeInstallations(installationsFile, Installations.markActive(known(), path));
            }
            return "JAVA_HOME set to " + path;
        });
    }

    private void adopt(Row row) {
        AdoptPlan plan;
        try {
            plan = new Adopter().plan(row.path(), root);
        } catch (Exception ex) {
            status.setText(ex.getMessage());
            return;
        }
        if (!ConfirmDialog.confirm(this, Confirmations.adopt(plan.source().toString(), plan.destination().toString(), plan.inPlace()))) {
            return;
        }
        submit(WorkQueue.Kind.ADOPT, progress -> {
            if (!plan.inPlace()) {
                progress.log("Copying " + plan.source() + " to " + plan.destination());
            }
            Installation adopted = new Adopter().adopt(plan, root, bundledRuntime);
            List<Installation> known = new ArrayList<>(known());
            known.removeIf(item -> Installations.samePath(item.path(), adopted.path()));
            known.add(adopted);
            stores.writeInstallations(installationsFile, known);
            progress.state(WorkQueue.State.UPDATING_TRUSTSTORE);
            new TrustSync().syncHomes(root, List.of(plan.destination()), bundledRuntime, progress::log);
            return "Adopted " + adopted.path();
        });
    }

    private void togglePin(Row row) {
        boolean pinned = !row.pinned();
        submit(WorkQueue.Kind.ADOPT, progress -> {
            stores.writeInstallations(installationsFile, Installations.withPinned(known(), row.path().toString(), pinned));
            return (pinned ? "Pinned " : "Unpinned ") + row.path();
        });
    }

    private void checkUpdates() {
        checkUpdates.setDisable(true);
        status.setText("Checking the catalog for newer builds");
        lookup.execute(() -> {
            String message;
            try {
                Settings current = settings.get();
                List<Distribution> vendors = browser.vendors(current, false);
                updates.clear();
                int checked = 0;
                for (Installation installation : known()) {
                    Distribution distribution = vendors.stream()
                            .filter(vendor -> Slug.of(vendor.apiParameter(), installation.javafxBundled(),
                                    installation.architecture()).equals(installation.vendorSlug()))
                            .findFirst()
                            .orElse(null);
                    if (distribution == null) {
                        continue;
                    }
                    checked++;
                    CatalogBrowser.Card card = browser.latest(distribution, installation.major(), current,
                            installation.javafxBundled(), true);
                    if (card.pkg() != null
                            && Slug.of(card.pkg().distribution(), card.pkg().javafxBundled(), card.pkg().architecture())
                                    .equals(installation.vendorSlug())
                            && Installations.newer(card.pkg().javaVersion(), installation.javaVersion())) {
                        updates.put(key(installation.path()), card);
                    }
                }
                message = updates.isEmpty()
                        ? "All " + checked + " managed JDKs with a catalog match are up to date."
                        : updates.size() + " update" + (updates.size() == 1 ? "" : "s") + " available. Pinned JDKs are not updated.";
            } catch (Exception ex) {
                message = "Could not check for updates: " + ex.getMessage();
            }
            String text = message;
            Platform.runLater(() -> {
                status.setText(text);
                checkUpdates.setDisable(false);
                refresh();
            });
        });
    }

    private void update(Row row) {
        CatalogBrowser.Card card = row.update();
        if (card == null || row.pinned()) {
            return;
        }
        if (!ConfirmDialog.confirm(this, Confirmations.update(row.path().toString(), row.installation().javaVersion(), card.pkg().javaVersion()))) {
            return;
        }
        queue.submit(new InstallJob(card, client, settings, queue, stores, () -> {
            updates.remove(key(row.path().toString()));
            status.setText("Updated " + row.path() + " to " + card.pkg().javaVersion());
            refresh();
        }));
    }

    private void remove(Row row) {
        String path = row.path().toString();
        if (!ConfirmDialog.confirm(this, Confirmations.removeJdk(path))) {
            return;
        }
        submit(WorkQueue.Kind.UNINSTALL, progress -> {
            String userHome = RegistryEnvironment.query(RegistryEnvironment.USER, "JAVA_HOME");
            String userPath = RegistryEnvironment.query(RegistryEnvironment.USER, "Path");
            String machineHome = RegistryEnvironment.query(RegistryEnvironment.MACHINE, "JAVA_HOME");
            String machinePath = RegistryEnvironment.query(RegistryEnvironment.MACHINE, "Path");
            if (PathUsage.uses(path, userHome, userPath, machineHome, machinePath)) {
                throw new IOException(path + " is still on PATH. Set another version first.");
            }
            List<Installation> known = new ArrayList<>(known());
            Installation target = known.stream().filter(item -> Installations.samePath(item.path(), path)).findFirst().orElse(null);
            if (target == null) {
                throw new IOException("Unmanaged homes are not deleted");
            }
            if (target.pinned()) {
                throw new IOException(path + " is pinned. Unpin it first.");
            }
            new Uninstaller().uninstall(target, root, bundledRuntime, null, null);
            known.remove(target);
            stores.writeInstallations(installationsFile, known);
            return "Removed " + path;
        });
    }

    private List<Installation> known() throws IOException {
        return Files.exists(installationsFile) ? stores.readInstallations(installationsFile) : List.of();
    }

    private static String key(String path) {
        return path.toLowerCase(java.util.Locale.ROOT);
    }

    @FunctionalInterface
    private interface Step {
        String run(WorkQueue.Progress progress) throws Exception;
    }

    /** Runs a change on the work queue, shows its result or error, then reloads the list. */
    private void submit(WorkQueue.Kind kind, Step step) {
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return kind;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                try {
                    String message = step.run(progress);
                    progress.log(message);
                    Platform.runLater(() -> {
                        status.setText(message);
                        refresh();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> status.setText(ex.getMessage()));
                    throw ex;
                }
            }
        });
    }
}
