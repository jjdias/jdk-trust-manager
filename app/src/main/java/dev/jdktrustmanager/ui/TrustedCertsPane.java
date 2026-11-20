package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.UrlEntry;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.ManagedTrust;
import dev.jdktrustmanager.trust.TrustSync;
import dev.jdktrustmanager.trust.UrlCatalog;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class TrustedCertsPane extends BorderPane {

    private final Path sourcesFile;
    private final UrlCatalog catalog = new UrlCatalog();
    private final TableView<UrlRow> table = new TableView<>();
    private final List<UrlEntry> urls = new ArrayList<>();
    private final TextField filter = new TextField();

    public TrustedCertsPane(Path root, Path installationsFile, Path bundledRuntime, WorkQueue queue) {
        sourcesFile = root.resolve("trusted-certs").resolve("sources.json");
        Label note = new Label("TestNG is the only built-in URL. Add the https sites whose certificates Selenium should trust. Each certificate is imported once into every JDK.");
        note.setWrapText(true);
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showUrls());
        Button syncAll = new Button("Sync all");
        syncAll.setOnAction(event -> {
            if (ConfirmDialog.confirm(this, Confirmations.syncAll())) {
                queue.submit(syncJob(root, installationsFile, bundledRuntime, this::reload));
            }
        });
        Button add = new Button("Add");
        add.setOnAction(event -> ask("Add URL", "").ifPresent(value -> change(() -> UrlCatalog.add(urls, value))));
        Button edit = new Button("Edit");
        edit.setOnAction(event -> selectedIndex().ifPresent(index -> ask("Edit URL", urls.get(index).url()).ifPresent(value -> change(() -> UrlCatalog.replace(urls, index, value)))));
        Button disable = new Button("Disable");
        disable.setOnAction(event -> selectedIndex().ifPresent(index -> change(() -> UrlCatalog.enabled(urls, index, !urls.get(index).enabled()))));
        Button remove = new Button("Remove");
        remove.setOnAction(event -> selectedIndex().ifPresent(index -> {
            if (ConfirmDialog.confirm(this, Confirmations.removeUrl(urls.get(index).url()))) {
                change(() -> UrlCatalog.remove(urls, index));
            }
        }));
        Button importFile = new Button("Import");
        importFile.setOnAction(event -> importUrls());
        Button exportFile = new Button("Export");
        exportFile.setOnAction(event -> exportUrls());
        TableColumn<UrlRow, String> urlColumn = new TableColumn<>("URL");
        urlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));
        urlColumn.setPrefWidth(520);
        TableColumn<UrlRow, String> stateColumn = new TableColumn<>("Enabled");
        stateColumn.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        table.getColumns().add(urlColumn);
        table.getColumns().add(stateColumn);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row == null) {
                disable.setText("Disable");
                return;
            }
            urls.stream()
                    .filter(entry -> entry.url().equals(row.getUrl()))
                    .findFirst()
                    .ifPresent(entry -> disable.setText(entry.enabled() ? "Disable" : "Enable"));
        });
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        setTop(new VBox(8, syncAll, note, new HBox(8, add, edit, disable, remove, importFile, exportFile), filter));
        setCenter(table);
        setPadding(new javafx.geometry.Insets(8));
        reload();
    }

    public static WorkQueue.Job syncJob(Path root, Path installationsFile, Path bundledRuntime, Runnable after) {
        return new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.TRUST_SYNC;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                progress.state(WorkQueue.State.UPDATING_TRUSTSTORE);
                new ManagedTrust().sync(root, installationsFile, bundledRuntime, progress::log);
                if (after != null) {
                    javafx.application.Platform.runLater(after);
                }
            }
        };
    }

    private void reload() {
        try {
            new TrustSync().ensureLayout(sourcesFile.getParent().getParent(), new JsonStores());
            urls.clear();
            urls.addAll(catalog.load(sourcesFile));
            showUrls();
        } catch (Exception ex) {
            table.setPlaceholder(new Label(ex.getMessage()));
        }
    }

    private void change(UrlChange change) {
        try {
            List<UrlEntry> next = change.apply();
            catalog.save(sourcesFile, next);
            reload();
        } catch (RuntimeException ex) {
            table.setPlaceholder(new Label(ex.getMessage()));
        } catch (Exception ex) {
            table.setPlaceholder(new Label(ex.getMessage()));
        }
    }

    private java.util.Optional<String> ask(String title, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText("https URL");
        return dialog.showAndWait();
    }

    private void showUrls() {
        var rows = FXCollections.<UrlRow>observableArrayList();
        for (UrlEntry entry : urls) {
            if (TextFilter.matches(entry.url(), filter.getText())) {
                rows.add(new UrlRow(entry.url(), entry.enabled() ? "yes" : "no"));
            }
        }
        table.setItems(rows);
    }

    private java.util.OptionalInt selectedIndex() {
        UrlRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return java.util.OptionalInt.empty();
        }
        for (int index = 0; index < urls.size(); index++) {
            if (urls.get(index).url().equals(selected.getUrl())) {
                return java.util.OptionalInt.of(index);
            }
        }
        return java.util.OptionalInt.empty();
    }

    private void importUrls() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import URLs");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            catalog.save(sourcesFile, UrlCatalog.merge(urls, UrlCatalog.importText(Files.readString(file.toPath()))));
            reload();
        } catch (Exception ex) {
            table.setPlaceholder(new Label(ex.getMessage()));
        }
    }

    private void exportUrls() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export URLs");
        chooser.setInitialFileName("certificate-urls.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), UrlCatalog.exportText(urls));
        } catch (Exception ex) {
            table.setPlaceholder(new Label(ex.getMessage()));
        }
    }

    @FunctionalInterface
    private interface UrlChange {
        List<UrlEntry> apply() throws Exception;
    }

    public static final class UrlRow {
        private final String url;
        private final String enabled;

        public UrlRow(String url, String enabled) {
            this.url = url;
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public String getEnabled() {
            return enabled;
        }
    }
}
