package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.env.EnvironmentPlan;
import dev.jdktrustmanager.env.JavaOnPath;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.JdkChoices;
import dev.jdktrustmanager.env.PathEditor;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.ShellSnippets;
import dev.jdktrustmanager.env.UserPathWriter;
import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.persist.JsonStores;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Java's variables and PATH entry, laid out like the Maven, Ant, and Gradle Environment screens: current values,
 * a JDK to choose, the other-entries checkbox, a PATH preview, Set and Remove from PATH, and snippets.
 */
public final class EnvironmentPane extends VBox {

    private final String root;
    private final Path installationsFile;
    private final JsonStores stores;
    private final TableView<JavaVariables.Row> variables = new TableView<>();
    private final ComboBox<String> jdk = new ComboBox<>();
    private final CheckBox removeOthers = new CheckBox();
    private final TextArea preview = new TextArea();
    private final TextArea snippet = new TextArea();
    private final Label status = new Label();
    private final Button set = new Button("Set");
    private final Button removeFromPath = new Button("Remove from PATH");
    private String userPath = "";
    private List<String> others = List.of();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-env");
        thread.setDaemon(true);
        return thread;
    });

    public EnvironmentPane(String root, Path installationsFile, JsonStores stores) {
        this.root = root;
        this.installationsFile = installationsFile;
        this.stores = stores;
        setSpacing(Spacing.GAP);
        setPadding(Spacing.PAGE);
        status.setWrapText(true);
        variables.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        variables.setPlaceholder(new Label("No Java variables are set."));
        variables.getColumns().add(column("Variable", 160, JavaVariables.Row::name));
        variables.getColumns().add(column("Scope", 90, JavaVariables.Row::scope));
        variables.getColumns().add(column("Value", 480, JavaVariables.Row::value));
        variables.setPrefHeight(170);
        jdk.setPromptText("No JDK found");
        jdk.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(jdk, Priority.ALWAYS);
        jdk.valueProperty().addListener((obs, old, home) -> updatePreview());
        preview.setEditable(false);
        preview.setPrefRowCount(5);
        snippet.setEditable(false);
        snippet.setPrefRowCount(4);
        VBox.setVgrow(snippet, Priority.ALWAYS);
        removeOthers.setOnAction(event -> updatePreview());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        set.setOnAction(event -> setSelected());
        removeFromPath.setOnAction(event -> removeFromPath());
        set.setDisable(true);
        removeFromPath.setDisable(true);
        Button powershell = new Button("PowerShell");
        powershell.setOnAction(event -> snippet(target -> ShellSnippets.powershell(target.home(), target.major())));
        Button maven = new Button("Maven toolchain");
        maven.setOnAction(event -> snippet(target -> ShellSnippets.mavenToolchain(target.home(), target.major())));
        Button gradle = new Button("Gradle note");
        gradle.setOnAction(event -> snippet(target -> ShellSnippets.gradleNote(target.home(), target.major())));
        HBox header = new HBox(Spacing.GAP, new Label("Java variables"), refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox chooser = new HBox(Spacing.GAP, new Label("JDK"), jdk);
        chooser.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(Spacing.GAP, set, removeFromPath, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox snippets = new HBox(Spacing.GAP, new Label("Snippets for this JDK"), powershell, maven, gradle);
        snippets.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(header, variables, chooser, removeOthers, new Label("New user PATH"), preview, actions, snippets, snippet);
        refresh();
    }

    private void refresh() {
        worker.execute(() -> {
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
                String javaHome = JavaVariables.find(user, "JAVA_HOME");
                List<String> choices = JdkChoices.candidates(javaHome, homes);
                List<String> found = PathEditor.otherJavaEntries(path, root);
                boolean onPath = List.of(path.split(";")).stream().anyMatch(entry -> entry.trim().equalsIgnoreCase(PathEditor.MANAGED));
                Platform.runLater(() -> {
                    userPath = path;
                    others = found;
                    variables.getItems().setAll(rows);
                    removeOthers.setText(found.isEmpty()
                            ? "No other Java entries on the user PATH"
                            : "Remove other Java entries: " + String.join(", ", found));
                    removeOthers.setDisable(found.isEmpty());
                    if (found.isEmpty()) {
                        removeOthers.setSelected(false);
                    }
                    String previous = jdk.getValue();
                    jdk.getItems().setAll(choices);
                    jdk.setValue(choices.contains(previous) ? previous : choices.isEmpty() ? null : choices.get(0));
                    removeFromPath.setDisable(!onPath && javaHome.isBlank());
                    updatePreview();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Couldn't read the environment: " + ex.getMessage());
                    set.setDisable(true);
                    removeFromPath.setDisable(true);
                });
            }
        });
    }

    private void updatePreview() {
        String home = jdk.getValue();
        set.setDisable(home == null || home.isBlank() || userPath.isBlank());
        if (home == null || home.isBlank()) {
            preview.setText("");
            snippet.setText("");
            return;
        }
        preview.setText(String.join("\n", EnvironmentPlan.forJava(userPath, root, home, removeOthers.isSelected()).path().split(";")));
        snippet(target -> ShellSnippets.powershell(target.home(), target.major()));
    }

    private void snippet(Function<JavaOnPath.Jdk, String> build) {
        String home = jdk.getValue();
        if (home == null || home.isBlank()) {
            status.setText("Select a JDK first.");
            return;
        }
        try {
            snippet.setText(build.apply(JavaOnPath.jdk(Path.of(home).resolve("bin").resolve("java.exe"))));
        } catch (Exception ex) {
            snippet.setText("");
            status.setText(ex.getMessage());
        }
    }

    private void setSelected() {
        String home = jdk.getValue();
        if (home == null || home.isBlank()) {
            status.setText("Select a JDK first.");
            return;
        }
        boolean removeOtherJava = removeOthers.isSelected();
        if (!ConfirmDialog.confirm(this, Confirmations.setJavaHome(home, removeOtherJava))) {
            return;
        }
        write(() -> {
            UserPathWriter.live().write(current -> EnvironmentPlan.forJava(current, root, home, removeOtherJava));
            markActive(home);
            return "JAVA_HOME set to " + home + ". Open a new terminal to use it.";
        });
    }

    private void removeFromPath() {
        if (!ConfirmDialog.confirm(this, Confirmations.removeJavaFromPath())) {
            return;
        }
        write(() -> {
            UserPathWriter.live().write(EnvironmentPlan::withoutJava);
            markActive(null);
            return "Java is no longer on the user PATH.";
        });
    }

    private void markActive(String home) throws java.io.IOException {
        if (Files.exists(installationsFile)) {
            stores.writeInstallations(installationsFile, Installations.markActive(stores.readInstallations(installationsFile), home));
        }
    }

    @FunctionalInterface
    private interface Step {
        String run() throws Exception;
    }

    private void write(Step step) {
        set.setDisable(true);
        removeFromPath.setDisable(true);
        worker.execute(() -> {
            try {
                String message = step.run();
                Platform.runLater(() -> status.setText(message));
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText(ex.getMessage()));
            }
            refresh();
        });
    }

    private static TableColumn<JavaVariables.Row, String> column(String title, double width, Function<JavaVariables.Row, String> value) {
        TableColumn<JavaVariables.Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }
}
