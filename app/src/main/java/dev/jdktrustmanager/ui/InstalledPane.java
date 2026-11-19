package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.env.PathEditor;
import dev.jdktrustmanager.env.PathUsage;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.UserEnvironment;
import dev.jdktrustmanager.install.Adopter;
import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.install.Uninstaller;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class InstalledPane extends BorderPane {

    private final Path bundledRuntime;
    private final List<String> installed = new ArrayList<>();
    private final TextField filter = new TextField();

    public InstalledPane(Path root, Path installationsFile, JsonStores stores, WorkQueue queue, Path bundledRuntime) {
        this.bundledRuntime = bundledRuntime;
        ListView<String> rows = new ListView<>();
        Label status = new Label();
        Button refresh = new Button("Refresh");
        Button setToPath = new Button("Set to path");
        Button remove = new Button("Remove");
        filter.setPromptText("Filter");
        filter.textProperty().addListener((obs, previous, text) -> showInstalled(rows));
        refresh.setOnAction(event -> queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.ADOPT;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                List<Installation> known = java.nio.file.Files.exists(installationsFile)
                        ? stores.readInstallations(installationsFile)
                        : List.of();
                List<JdkScanner.Discovered> found = new JdkScanner().scan(root, known, List.of());
                javafx.application.Platform.runLater(() -> {
                    installed.clear();
                    found.forEach(row -> installed.add((row.managed() ? "managed " : "adopt ") + row.path()));
                    showInstalled(rows);
                });
            }
        }));
        setToPath.setOnAction(event -> {
            String path = selectedPath(rows);
            if (path == null) {
                status.setText("Select a version");
                return;
            }
            queue.submit(new WorkQueue.Job() {
                @Override
                public WorkQueue.Kind kind() {
                    return WorkQueue.Kind.ADOPT;
                }

                @Override
                public void run(WorkQueue.Progress progress) throws Exception {
                    try {
                        String current = RegistryEnvironment.query(RegistryEnvironment.USER, "Path");
                        if (current.isBlank()) {
                            throw new IOException("User Path could not be read");
                        }
                        String next = PathEditor.edit(current, root.toString(), false);
                        new UserEnvironment().apply(path, next);
                        if (java.nio.file.Files.exists(installationsFile)) {
                            stores.writeInstallations(installationsFile,
                                    Installations.markActive(stores.readInstallations(installationsFile), path));
                        }
                        progress.log("JAVA_HOME set to " + path);
                        javafx.application.Platform.runLater(() -> status.setText("JAVA_HOME set to " + path));
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> status.setText(ex.getMessage()));
                        throw ex;
                    }
                }
            });
        });
        remove.setOnAction(event -> {
            String path = selectedPath(rows);
            if (path == null) {
                status.setText("Select a version");
                return;
            }
            queue.submit(new WorkQueue.Job() {
                @Override
                public WorkQueue.Kind kind() {
                    return WorkQueue.Kind.UNINSTALL;
                }

                @Override
                public void run(WorkQueue.Progress progress) throws Exception {
                    try {
                        if (!java.nio.file.Files.exists(installationsFile)) {
                            throw new IOException("Unmanaged homes are not deleted");
                        }
                        String userHome = RegistryEnvironment.query(RegistryEnvironment.USER, "JAVA_HOME");
                        String userPath = RegistryEnvironment.query(RegistryEnvironment.USER, "Path");
                        String machineHome = RegistryEnvironment.query(RegistryEnvironment.MACHINE, "JAVA_HOME");
                        String machinePath = RegistryEnvironment.query(RegistryEnvironment.MACHINE, "Path");
                        if (PathUsage.uses(path, userHome, userPath, machineHome, machinePath)) {
                            throw new IOException(path + " is still on PATH. Set another version first.");
                        }
                        List<Installation> known = new ArrayList<>(stores.readInstallations(installationsFile));
                        Installation target = known.stream().filter(item -> item.path().equals(path)).findFirst().orElse(null);
                        if (target == null || target.pinned()) {
                            throw new IOException(target == null ? "Unmanaged homes are not deleted" : "Pinned");
                        }
                        new Uninstaller().uninstall(target, root, bundledRuntime, null, null);
                        known.remove(target);
                        stores.writeInstallations(installationsFile, known);
                        progress.log("Removed " + path);
                        javafx.application.Platform.runLater(() -> {
                            installed.removeIf(row -> path.equals(selectedPath(row)));
                            showInstalled(rows);
                            status.setText("Removed " + path);
                        });
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> status.setText(ex.getMessage()));
                        throw ex;
                    }
                }
            });
        });
        setTop(new VBox(8, filter, new HBox(8, refresh, setToPath, remove, status)));
        setCenter(rows);
        new Adopter();
        refresh.fire();
    }

    private void showInstalled(ListView<String> rows) {
        rows.getItems().setAll(installed.stream().filter(row -> TextFilter.matches(row, filter.getText())).toList());
    }

    private static String selectedPath(ListView<String> rows) {
        return selectedPath(rows.getSelectionModel().getSelectedItem());
    }

    private static String selectedPath(String row) {
        if (row == null) {
            return null;
        }
        int space = row.indexOf(' ');
        if (space < 0 || space == row.length() - 1) {
            return null;
        }
        return row.substring(space + 1);
    }
}
