package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.JdkPicker;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.buildtools.ToolRelease;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.install.ZipInstaller;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.model.ToolInstallation;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Published versions of one tool, newest first, with Install above the list. */
final class ToolVersionsPane extends BorderPane {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final BuildTool tool;
    private final ToolContext context;
    private final Runnable changed;
    private final ListView<ToolRelease> list = new ListView<>();
    private final TextField filter = new TextField();
    private final Label status = new Label();
    private final Button install = new Button("Install");
    private final List<ToolRelease> releases = new ArrayList<>();
    private Set<String> installed = Set.of();
    private final ExecutorService lookup = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-tool-versions");
        thread.setDaemon(true);
        return thread;
    });

    ToolVersionsPane(BuildTool tool, ToolContext context, Runnable changed) {
        this.tool = tool;
        this.context = context;
        this.changed = changed;
        Button reload = new Button("Refresh list");
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, old, text) -> show());
        status.setWrapText(true);
        list.setPlaceholder(new Label("Loading " + tool.displayName() + " versions…"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ToolRelease release, boolean empty) {
                super.updateItem(release, empty);
                setText(empty || release == null ? null : label(release));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, release) ->
                install.setDisable(release == null || installed.contains(release.version())));
        install.setDisable(true);
        install.setOnAction(event -> installSelected());
        reload.setOnAction(event -> load(true));
        HBox actions = new HBox(Spacing.GAP, install, reload, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        setTop(new VBox(Spacing.GAP, actions, filter));
        setCenter(list);
        Spacing.page(this);
        load(false);
    }

    void refresh() {
        load(false);
    }

    private String label(ToolRelease release) {
        String text = release.version();
        if (installed.contains(release.version())) {
            text += "   installed";
        }
        if (release.preRelease()) {
            text += "   pre-release";
        }
        return text;
    }

    private void load(boolean force) {
        lookup.execute(() -> {
            try {
                Settings settings = context.settings().get();
                var load = context.catalog().releases(tool, settings.catalogMaxAgeHours(), force);
                Set<String> versions = ToolLayout.installed(context.root(), tool).stream()
                        .map(home -> home.getFileName().toString().substring(tool.folderPrefix().length() + 1))
                        .collect(Collectors.toSet());
                String note = load.stale() && context.catalog().fetchedAt(tool.tool()) != null
                        ? "Offline: showing the list from " + WHEN.format(context.catalog().fetchedAt(tool.tool()))
                        : "";
                Platform.runLater(() -> {
                    releases.clear();
                    releases.addAll(load.value().stream()
                            .filter(release -> settings.showBuildToolPreReleases() || !release.preRelease())
                            .toList());
                    installed = versions;
                    status.setText(note);
                    show();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Couldn't load the " + tool.displayName() + " version list: " + ex.getMessage());
                    list.setPlaceholder(new Label(ex.getMessage()));
                });
            }
        });
    }

    private void show() {
        ToolRelease selected = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(releases.stream().filter(release -> TextFilter.matches(release.version(), filter.getText())).toList());
        if (selected != null) {
            list.getItems().stream().filter(item -> item.version().equals(selected.version())).findFirst()
                    .ifPresent(item -> list.getSelectionModel().select(item));
        }
        ToolRelease now = list.getSelectionModel().getSelectedItem();
        install.setDisable(now == null || installed.contains(now.version()));
    }

    private void installSelected() {
        ToolRelease release = list.getSelectionModel().getSelectedItem();
        if (release == null) {
            return;
        }
        Path folder = ToolLayout.destination(context.root(), tool, release.version());
        if (!ConfirmDialog.confirm(this, Confirmations.installTool(tool.displayName(), release.version(), folder.toString(),
                tool.keysUrl().isPresent()))) {
            return;
        }
        WorkQueue queue = context.queue();
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.INSTALL;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                progress.log("Install " + tool.displayName() + " " + release.version());
                progress.state(WorkQueue.State.DOWNLOADING);
                try {
                    var java = JdkPicker.pick(tool.javaRange(release.version()), context.activeJavaHome(), context.managedJdks());
                    ToolInstallation row = context.installer().install(tool, release, context.root(), java, adapter(progress, queue));
                    context.save(row);
                    String message = row.verification().weak()
                            ? "Installed " + tool.displayName() + " " + release.version()
                                    + ". Weak checksum: verified with " + row.verification().name().replace("SHA1", "SHA-1")
                                    + " only, because Apache's signing keys couldn't be reached."
                            : "Installed " + tool.displayName() + " " + release.version() + " in " + row.path();
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

    static ZipInstaller.InstallProgress adapter(WorkQueue.Progress progress, WorkQueue queue) {
        return new ZipInstaller.InstallProgress() {
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

            @Override
            public boolean cancelled() {
                return progress.cancelled();
            }
        };
    }
}
