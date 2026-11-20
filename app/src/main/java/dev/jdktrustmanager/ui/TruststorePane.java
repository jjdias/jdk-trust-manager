package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.env.JavaOnPath;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.model.CertificateEntry;
import dev.jdktrustmanager.trust.KeytoolStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public final class TruststorePane extends BorderPane {

    private final ListView<String> rows = new ListView<>();
    private final List<String> certificates = new ArrayList<>();
    private final TextField filter = new TextField();
    private final Label source = new Label("Resolving java on PATH");

    public TruststorePane() {
        source.setWrapText(true);
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showCertificates());
        Button load = new Button("Load cacerts");
        load.setOnAction(event -> load());
        setTop(new VBox(Spacing.GAP, load, source, filter));
        setCenter(rows);
        Spacing.page(this);
        load();
    }

    private void load() {
        Thread worker = new Thread(() -> {
            try {
                Map<String, String> user = RegistryEnvironment.queryAll(RegistryEnvironment.USER);
                Map<String, String> machine = RegistryEnvironment.queryAll(RegistryEnvironment.MACHINE);
                Map<String, String> variables = new java.util.LinkedHashMap<>(machine);
                variables.putAll(user);
                Path exe = JavaOnPath.findJava(JavaVariables.find(machine, "Path"), JavaVariables.find(user, "Path"), variables);
                if (exe == null) {
                    throw new java.io.IOException("No java.exe is on PATH");
                }
                JavaOnPath.Jdk jdk = JavaOnPath.jdk(exe);
                java.util.List<CertificateEntry> entries = new KeytoolStore().listCacerts(jdk.home(), jdk.major());
                javafx.application.Platform.runLater(() -> {
                    source.setText(jdk.home().resolve("lib").resolve("security").resolve("cacerts").toString());
                    certificates.clear();
                    entries.forEach(entry -> certificates.add(entry.alias() + " " + entry.sha256()));
                    showCertificates();
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

    private void showCertificates() {
        rows.getItems().setAll(certificates.stream().filter(row -> TextFilter.matches(row, filter.getText())).toList());
    }
}
