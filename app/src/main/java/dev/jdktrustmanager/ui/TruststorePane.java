package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.env.JavaOnPath;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.model.CertificateEntry;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.HtmlReport;
import dev.jdktrustmanager.trust.KeytoolStore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class TruststorePane extends BorderPane {

    private final ListView<String> rows = new ListView<>();
    private final List<String> certificates = new ArrayList<>();
    private final TextField filter = new TextField();
    private final Label source = new Label("Resolving java on PATH");
    private final Label message = new Label();
    private final Button report = new Button("Save report…");
    private final Path installationsFile;
    private final JsonStores stores;
    private final Consumer<String> openDocument;
    private List<CertificateEntry> entries = List.of();
    private String cacerts = "";
    private String javaHome = "";

    public TruststorePane(Path installationsFile, JsonStores stores, Consumer<String> openDocument) {
        this.installationsFile = installationsFile;
        this.stores = stores;
        this.openDocument = openDocument;
        source.setWrapText(true);
        message.setWrapText(true);
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showCertificates());
        Button load = new Button("Load cacerts");
        load.setOnAction(event -> load());
        report.setDisable(true);
        report.setOnAction(event -> saveReport());
        HBox actions = new HBox(Spacing.GAP, load, report, message);
        actions.setAlignment(Pos.CENTER_LEFT);
        setTop(new VBox(Spacing.GAP, actions, source, filter));
        setCenter(rows);
        Spacing.page(this);
        load();
    }

    private void load() {
        report.setDisable(true);
        Thread worker = new Thread(() -> {
            try {
                Map<String, String> user = RegistryEnvironment.queryAll(RegistryEnvironment.USER);
                Map<String, String> machine = RegistryEnvironment.queryAll(RegistryEnvironment.MACHINE);
                Map<String, String> variables = new java.util.LinkedHashMap<>(machine);
                variables.putAll(user);
                String home = JavaVariables.find(user, "JAVA_HOME");
                if (home == null || home.isBlank()) {
                    home = JavaVariables.find(machine, "JAVA_HOME");
                }
                Path exe = JavaOnPath.findJava(JavaVariables.find(machine, "Path"), JavaVariables.find(user, "Path"), variables);
                if (exe == null) {
                    throw new java.io.IOException("No java.exe is on PATH");
                }
                JavaOnPath.Jdk jdk = JavaOnPath.jdk(exe);
                List<CertificateEntry> loaded = new KeytoolStore().listCacerts(jdk.home(), jdk.major());
                String resolvedHome = home == null ? "" : home;
                javafx.application.Platform.runLater(() -> {
                    entries = loaded;
                    javaHome = resolvedHome;
                    cacerts = jdk.home().resolve("lib").resolve("security").resolve("cacerts").toString();
                    source.setText(cacerts);
                    certificates.clear();
                    loaded.forEach(entry -> certificates.add(entry.alias() + " " + entry.sha256()));
                    showCertificates();
                    report.setDisable(false);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    source.setText(ex.getMessage());
                    certificates.clear();
                    certificates.add(ex.getMessage());
                    showCertificates();
                });
            }
        }, "jkm-keytool");
        worker.setDaemon(true);
        worker.start();
    }

    private void saveReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        chooser.setInitialFileName("jdk-trust-report.html");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML", "*.html"));
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            List<Installation> installations = Files.exists(installationsFile) ? stores.readInstallations(installationsFile) : List.of();
            String html = new HtmlReport().render(installations, javaHome, cacerts, entries, Instant.now());
            Files.writeString(file.toPath(), html);
            message.setText("Saved " + file);
            if (openDocument != null) {
                openDocument.accept(file.toURI().toString());
            }
        } catch (Exception ex) {
            message.setText("Could not save the report: " + ex.getMessage());
        }
    }

    private void showCertificates() {
        rows.getItems().setAll(certificates.stream().filter(row -> TextFilter.matches(row, filter.getText())).toList());
    }
}
