package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.ToolAdopter;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.env.EnvironmentPlan;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.UserPathWriter;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolInstallations;
import dev.jdktrustmanager.model.Verification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Installed versions of one tool: which is on PATH, pin, adopt, remove. */
final class ToolInstalledPane extends BorderPane {

    private final BuildTool tool;
    private final ToolContext context;
    private final Runnable changed;
    private final ListView<ToolInstallation> list = new ListView<>();
    private final Label status = new Label();
    private final Button setToPath = new Button("Set to path");
    private final Button pin = new Button("Pin");
    private final Button remove = new Button("Remove");
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-tool-installed");
        thread.setDaemon(true);
        return thread;
    });

    ToolInstalledPane(BuildTool tool, ToolContext context, Runnable changed) {
        this.tool = tool;
        this.context = context;
        this.changed = changed;
        Button refresh = new Button("Refresh");
        Button adopt = new Button("Adopt…");
        status.setWrapText(true);
        list.setPlaceholder(new Label("No " + tool.displayName() + " versions installed. Install one from Versions, or Adopt an existing one."));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ToolInstallation row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : label(row));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> enableFor(row));
        refresh.setOnAction(event -> refresh());
        setToPath.setOnAction(event -> selected(this::setToPath));
        adopt.setOnAction(event -> adopt());
        pin.setOnAction(event -> selected(this::togglePin));
        remove.setOnAction(event -> selected(this::remove));
        HBox actions = new HBox(Spacing.GAP, refresh, setToPath, adopt, pin, remove);
        actions.setAlignment(Pos.CENTER_LEFT);
        setTop(new VBox(Spacing.GAP, actions, status));
        setCenter(list);
        Spacing.page(this);
        enableFor(null);
        refresh();
    }

    static String label(ToolInstallation row) {
        StringBuilder text = new StringBuilder(row.version()).append("   ").append(row.path());
        if (row.active()) {
            text.append("   [on PATH]");
        }
        if (row.pinned()) {
            text.append("   [pinned]");
        }
        text.append("   [").append(verification(row.verification())).append(']');
        return text.toString();
    }

    private static String verification(Verification verification) {
        return switch (verification) {
            case SIGNATURE -> "signature";
            case SHA512 -> "SHA-512";
            case SHA256 -> "SHA-256";
            case SHA1, MD5 -> "weak checksum";
            case NONE -> "adopted";
            case UNRECORDED -> "unrecorded";
        };
    }

    void refresh() {
        lookup.execute(() -> {
            try {
                List<ToolInstallation> rows = context.tools(tool);
                Platform.runLater(() -> {
                    ToolInstallation before = list.getSelectionModel().getSelectedItem();
                    list.getItems().setAll(rows);
                    if (before != null) {
                        rows.stream().filter(row -> row.path().equals(before.path())).findFirst()
                                .ifPresent(row -> list.getSelectionModel().select(row));
                    }
                    enableFor(list.getSelectionModel().getSelectedItem());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText(ex.getMessage()));
            }
        });
    }

    private void enableFor(ToolInstallation row) {
        setToPath.setDisable(row == null || row.active());
        pin.setDisable(row == null);
        pin.setText(row != null && row.pinned() ? "Unpin" : "Pin");
        remove.setDisable(row == null || row.active() || row.pinned());
    }

    private void selected(java.util.function.Consumer<ToolInstallation> action) {
        ToolInstallation row = list.getSelectionModel().getSelectedItem();
        if (row == null) {
            status.setText("Select a version");
            return;
        }
        action.accept(row);
    }

    private void setToPath(ToolInstallation row) {
        if (!ConfirmDialog.confirm(this, Confirmations.setToolPath(tool.displayName(), row.version(),
                context.javaWarning(tool, row.version())))) {
            return;
        }
        submit(WorkQueue.Kind.ADOPT, progress -> {
            UserPathWriter.live().write(current -> EnvironmentPlan.forTool(current, context.root().toString(), tool, row.path(), List.of()));
            context.update(all -> ToolInstallations.markActive(all, tool.tool(), row.path()));
            return tool.displayName() + " " + row.version() + " is on PATH. Open a new terminal to use it.";
        });
    }

    private void togglePin(ToolInstallation row) {
        boolean pinned = !row.pinned();
        submit(WorkQueue.Kind.ADOPT, progress -> {
            context.update(all -> ToolInstallations.withPinned(all, row.path(), pinned));
            return (pinned ? "Pinned " : "Unpinned ") + tool.displayName() + " " + row.version();
        });
    }

    private void remove(ToolInstallation row) {
        if (!ConfirmDialog.confirm(this, Confirmations.removeTool(tool.displayName(), row.version(), row.path()))) {
            return;
        }
        submit(WorkQueue.Kind.UNINSTALL, progress -> {
            context.installer().uninstall(row, context.root());
            context.remove(row);
            return "Removed " + tool.displayName() + " " + row.version();
        });
    }

    private void adopt() {
        status.setText("Looking for existing " + tool.displayName() + " installs…");
        lookup.execute(() -> {
            try {
                Map<String, String> user = RegistryEnvironment.queryAll(RegistryEnvironment.USER);
                Map<String, String> machine = RegistryEnvironment.queryAll(RegistryEnvironment.MACHINE);
                Map<String, String> variables = ToolContext.variables();
                List<Path> found = ToolAdopter.candidates(tool, variables, ToolContext.combinedPath(user, machine),
                        ToolAdopter.defaultRoots(tool, System.getenv()));
                Platform.runLater(() -> chooseAndAdopt(found));
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText(ex.getMessage()));
            }
        });
    }

    private void chooseAndAdopt(List<Path> found) {
        if (found.isEmpty()) {
            status.setText("No other " + tool.displayName() + " installs were found.");
            return;
        }
        status.setText("");
        ChoiceDialog<Path> dialog = new ChoiceDialog<>(found.get(0), found);
        dialog.setTitle("Adopt " + tool.displayName());
        dialog.setHeaderText("Copy an existing " + tool.displayName() + " install into " + ToolLayout.folder(context.root()));
        dialog.setContentText("Install");
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(home -> {
            String version = ToolAdopter.versionOf(tool, home).orElse(null);
            if (version == null) {
                status.setText("Couldn't tell which " + tool.displayName() + " version " + home + " is.");
                return;
            }
            Path destination = ToolLayout.destination(context.root(), tool, version);
            if (!ConfirmDialog.confirm(this, Confirmations.adopt(home.toString(), destination.toString(), false))) {
                return;
            }
            submit(WorkQueue.Kind.ADOPT, progress -> {
                ToolInstallation row = context.adopter().adopt(tool, home, context.root());
                context.save(row);
                return "Adopted " + tool.displayName() + " " + row.version() + " into " + row.path();
            });
        });
    }

    @FunctionalInterface
    private interface Step {
        String run(WorkQueue.Progress progress) throws Exception;
    }

    private void submit(WorkQueue.Kind kind, Step step) {
        context.queue().submit(new WorkQueue.Job() {
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
