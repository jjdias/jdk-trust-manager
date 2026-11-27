package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.env.JavaOnPath;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.JdkChoices;
import dev.jdktrustmanager.env.PathEditor;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.ShellSnippets;
import dev.jdktrustmanager.env.UserEnvironment;
import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class EnvironmentPane extends VBox {

    private final String root;
    private final Path installationsFile;
    private final JsonStores stores;
    private final TableView<JavaVariables.Row> variables = new TableView<>();
    private final ComboBox<String> jdk = new ComboBox<>();
    private final TextArea preview = new TextArea();
    private final TextArea snippet = new TextArea();
    private final CheckBox removeOthers = new CheckBox("Remove other Java PATH entries");
    private final Button apply = new Button("Set JAVA_HOME (user scope)");
    private final Label status = new Label();
    private String rawUserPath = "";

    public EnvironmentPane(String root, Path installationsFile, JsonStores stores) {
        this.root = root;
        this.installationsFile = installationsFile;
        this.stores = stores;
        setSpacing(Spacing.GAP);
        setPadding(Spacing.PAGE);
        jdk.setPromptText("No JDK found");
        jdk.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(jdk, Priority.ALWAYS);
        variables.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        variables.setPlaceholder(new Label("No Java variables are set."));
        variables.getColumns().add(column("Variable", 160, JavaVariables.Row::name));
        variables.getColumns().add(column("Scope", 90, JavaVariables.Row::scope));
        variables.getColumns().add(column("Value", 480, JavaVariables.Row::value));
        VBox.setVgrow(variables, Priority.ALWAYS);
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> reload());
        getChildren().addAll(javaVariablesHeader(refresh), variables, advanced());
        reload();
    }

    private HBox javaVariablesHeader(Button refresh) {
        HBox header = new HBox(Spacing.GAP, new Label("Java variables"), refresh, status);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return header;
    }

    private TitledPane advanced() {
        preview.setEditable(false);
        preview.setPrefRowCount(4);
        snippet.setEditable(false);
        snippet.setPrefRowCount(5);
        apply.setDisable(true);
        removeOthers.setOnAction(event -> preview.setText(PathEditor.edit(rawUserPath, root, removeOthers.isSelected())));
        apply.setOnAction(event -> applyUserScope());
        Button powershell = new Button("PowerShell");
        powershell.setOnAction(event -> withSelected(target -> snippet.setText(ShellSnippets.powershell(target.home(), target.major()))));
        Button maven = new Button("Maven toolchain");
        maven.setOnAction(event -> withSelected(target -> snippet.setText(ShellSnippets.mavenToolchain(target.home(), target.major()))));
        Button gradle = new Button("Gradle note");
        gradle.setOnAction(event -> withSelected(target -> snippet.setText(ShellSnippets.gradleNote(target.home(), target.major()))));
        VBox contents = new VBox(Spacing.GAP,
                new HBox(Spacing.GAP, new Label("JDK"), jdk),
                new Label("Already-open terminals keep the old environment."),
                preview,
                removeOthers,
                apply,
                new HBox(Spacing.GAP, powershell, maven, gradle),
                snippet);
        TitledPane pane = new TitledPane("Advanced commands", contents);
        pane.setExpanded(false);
        return pane;
    }

    private void withSelected(java.util.function.Consumer<JavaOnPath.Jdk> action) {
        String selected = jdk.getValue();
        if (selected == null || selected.isBlank()) {
            status.setText("Select a JDK first.");
            return;
        }
        try {
            action.accept(JavaOnPath.jdk(Path.of(selected).resolve("bin").resolve("java.exe")));
        } catch (Exception ex) {
            status.setText(ex.getMessage());
        }
    }

    private void applyUserScope() {
        String next = preview.getText();
        String selected = jdk.getValue();
        if (next.isBlank()) {
            status.setText("User Path could not be read");
            return;
        }
        if (selected == null || selected.isBlank()) {
            status.setText("Select a JDK first.");
            return;
        }
        if (!ConfirmDialog.confirm(this, Confirmations.setJavaHome(selected, removeOthers.isSelected()))) {
            return;
        }
        boolean removeOtherJava = removeOthers.isSelected();
        Thread worker = new Thread(() -> {
            try {
                dev.jdktrustmanager.env.UserPathWriter.live()
                        .write(current -> dev.jdktrustmanager.env.EnvironmentPlan.forJava(current, root, selected, removeOtherJava));
                if (Files.exists(installationsFile)) {
                    stores.writeInstallations(installationsFile,
                            Installations.markActive(stores.readInstallations(installationsFile), selected));
                }
                javafx.application.Platform.runLater(() -> snippet.setText("JAVA_HOME set to " + selected));
                reload();
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    status.setText(ex.getMessage());
                    snippet.setText(ex.getMessage());
                });
            }
        }, "jkm-env");
        worker.setDaemon(true);
        worker.start();
    }

    private void reload() {
        Thread worker = new Thread(() -> {
            try {
                Map<String, String> user = RegistryEnvironment.queryAll(RegistryEnvironment.USER);
                Map<String, String> machine = RegistryEnvironment.queryAll(RegistryEnvironment.MACHINE);
                var rows = JavaVariables.list(user, machine, root);
                String path = JavaVariables.find(user, "Path");
                List<Installation> known = Files.exists(installationsFile) ? stores.readInstallations(installationsFile) : List.of();
                List<String> homes = new ArrayList<>();
                known.forEach(item -> homes.add(item.path()));
                for (JdkScanner.Discovered found : new JdkScanner().scan(Path.of(root), List.of(), known, List.of())) {
                    homes.add(found.path().toString());
                }
                List<String> choices = JdkChoices.candidates(JavaVariables.find(user, "JAVA_HOME"), homes);
                javafx.application.Platform.runLater(() -> {
                    String previous = jdk.getValue();
                    jdk.getItems().setAll(choices);
                    jdk.setValue(choices.contains(previous) ? previous : choices.isEmpty() ? null : choices.get(0));
                    rawUserPath = path;
                    variables.getItems().setAll(rows);
                    preview.setText(path.isBlank() ? "" : PathEditor.edit(path, root, removeOthers.isSelected()));
                    apply.setDisable(path.isBlank());
                    status.setText("");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> status.setText(ex.getMessage()));
            }
        }, "jkm-env-read");
        worker.setDaemon(true);
        worker.start();
    }

    private static TableColumn<JavaVariables.Row, String> column(String title, double width, java.util.function.Function<JavaVariables.Row, String> value) {
        TableColumn<JavaVariables.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }
}
