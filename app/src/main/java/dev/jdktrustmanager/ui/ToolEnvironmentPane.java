package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.env.EnvironmentPlan;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.PathEditor;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.UserEnvironment;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolInstallations;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One tool's variables and PATH entry: preview, set, remove from PATH, clean up other entries, PowerShell snippet. */
final class ToolEnvironmentPane extends VBox {

    private final BuildTool tool;
    private final ToolContext context;
    private final Runnable changed;
    private final Label current = new Label();
    private final ComboBox<ToolInstallation> version = new ComboBox<>();
    private final CheckBox removeOthers = new CheckBox();
    private final TextArea preview = new TextArea();
    private final TextArea snippet = new TextArea();
    private final Label status = new Label();
    private final Button set = new Button("Set");
    private final Button removeFromPath = new Button("Remove from PATH");
    private String userPath = "";
    private List<String> others = List.of();
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-tool-env");
        thread.setDaemon(true);
        return thread;
    });

    ToolEnvironmentPane(BuildTool tool, ToolContext context, Runnable changed) {
        this.tool = tool;
        this.context = context;
        this.changed = changed;
        setSpacing(Spacing.GAP);
        setPadding(Spacing.PAGE);
        current.setWrapText(true);
        status.setWrapText(true);
        preview.setEditable(false);
        preview.setPrefRowCount(5);
        snippet.setEditable(false);
        snippet.setPrefRowCount(3);
        version.setPromptText("No " + tool.displayName() + " installed");
        version.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(version, Priority.ALWAYS);
        version.setCellFactory(view -> cell());
        version.setButtonCell(cell());
        version.valueProperty().addListener((obs, old, row) -> updatePreview());
        removeOthers.setOnAction(event -> updatePreview());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        set.setOnAction(event -> setSelected());
        removeFromPath.setOnAction(event -> removeFromPath());
        HBox header = new HBox(Spacing.GAP, new Label(tool.displayName() + " variables"), refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox chooser = new HBox(Spacing.GAP, new Label("Version"), version);
        chooser.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(Spacing.GAP, set, removeFromPath, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(header, current, chooser, removeOthers, new Label("New user PATH"), preview, actions,
                new Label("PowerShell, this window only"), snippet);
        refresh();
    }

    private static ListCell<ToolInstallation> cell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ToolInstallation row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : row.version() + "   " + row.path());
            }
        };
    }

    void refresh() {
        lookup.execute(() -> {
            try {
                Map<String, String> user = RegistryEnvironment.queryAll(RegistryEnvironment.USER);
                List<ToolInstallation> rows = context.tools(tool);
                String path = JavaVariables.find(user, "Path");
                List<String> found = PathEditor.otherEntriesFor(path, context.root().toString(), tool);
                StringBuilder text = new StringBuilder();
                for (String variable : tool.homeVariables()) {
                    String value = JavaVariables.find(user, variable);
                    text.append(variable).append(" = ").append(value.isBlank() ? "(not set)" : value).append('\n');
                }
                boolean onPath = List.of(path.split(";")).stream().anyMatch(entry -> entry.trim().equalsIgnoreCase(tool.pathEntry()));
                text.append(tool.pathEntry()).append(onPath ? " is on the user PATH" : " is not on the user PATH");
                Platform.runLater(() -> {
                    userPath = path;
                    others = found;
                    current.setText(text.toString());
                    removeOthers.setText(found.isEmpty()
                            ? "No other " + tool.displayName() + " entries on the user PATH"
                            : "Remove other " + tool.displayName() + " entries: " + String.join(", ", found));
                    removeOthers.setDisable(found.isEmpty());
                    if (found.isEmpty()) {
                        removeOthers.setSelected(false);
                    }
                    ToolInstallation keep = version.getValue();
                    version.getItems().setAll(rows);
                    rows.stream().filter(row -> keep != null ? row.path().equals(keep.path()) : row.active())
                            .findFirst().or(() -> rows.stream().findFirst())
                            .ifPresent(version::setValue);
                    removeFromPath.setDisable(!onPath && JavaVariables.find(user, tool.homeVariables().get(0)).isBlank());
                    updatePreview();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText(ex.getMessage()));
            }
        });
    }

    private EnvironmentPlan plan(String home) {
        return EnvironmentPlan.forTool(userPath, context.root().toString(), tool, home,
                removeOthers.isSelected() ? others : List.of());
    }

    private void updatePreview() {
        ToolInstallation row = version.getValue();
        set.setDisable(row == null || userPath.isBlank());
        if (row == null) {
            preview.setText("");
            snippet.setText("");
            return;
        }
        EnvironmentPlan plan = plan(row.path());
        preview.setText(String.join("\n", plan.path().split(";")));
        StringBuilder text = new StringBuilder();
        for (String variable : tool.homeVariables()) {
            text.append("$env:").append(variable).append(" = '").append(row.path()).append("'; ");
        }
        text.append("$env:Path = '").append(row.path()).append("\\bin;' + $env:Path");
        snippet.setText(text.toString());
    }

    private void setSelected() {
        ToolInstallation row = version.getValue();
        if (row == null) {
            return;
        }
        if (!ConfirmDialog.confirm(this, Confirmations.setToolPath(tool.displayName(), row.version(),
                context.javaWarning(tool, row.version())))) {
            return;
        }
        EnvironmentPlan plan = plan(row.path());
        submit(() -> {
            new UserEnvironment().apply(plan);
            context.update(all -> ToolInstallations.markActive(all, tool.tool(), row.path()));
            return tool.displayName() + " " + row.version() + " is on PATH. Open a new terminal to use it.";
        });
    }

    private void removeFromPath() {
        if (!ConfirmDialog.confirm(this, Confirmations.removeToolFromPath(tool.displayName(), tool.homeVariables()))) {
            return;
        }
        EnvironmentPlan plan = plan(null);
        submit(() -> {
            new UserEnvironment().apply(plan);
            context.update(all -> ToolInstallations.markActive(all, tool.tool(), null));
            return tool.displayName() + " is no longer on PATH.";
        });
    }

    @FunctionalInterface
    private interface Step {
        String run() throws Exception;
    }

    private void submit(Step step) {
        context.queue().submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.ADOPT;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                try {
                    String message = step.run();
                    progress.log(message);
                    Platform.runLater(() -> {
                        status.setText(message);
                        changed.run();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> status.setText(ex.getMessage()));
                    throw ex;
                }
            }
        });
    }
}
