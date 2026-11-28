package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.profiles.CurrentSetup;
import dev.jdktrustmanager.profiles.ProfileResolver;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** List, create, edit, delete, and activate profiles. Every write goes through the work queue and UserPathWriter. */
public final class ProfilesPane extends BorderPane {

    private final ToolContext context;
    private final ProfileStore store;
    private final ProfileActivator activator;
    private final ListView<Profile> list = new ListView<>();
    private final Label activeLine = new Label();
    private final Label status = new Label();
    private final TextArea details = new TextArea();
    private final Button create = new Button("New");
    private final Button saveCurrent = new Button("Save current setup");
    private final Button edit = new Button("Edit");
    private final Button delete = new Button("Delete");
    private final Button activate = new Button("Activate");
    private ProfileState snapshot = ProfileState.empty();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-profiles");
        thread.setDaemon(true);
        return thread;
    });

    public ProfilesPane(ToolContext context, ProfileStore store, ProfileActivator activator) {
        this.context = context;
        this.store = store;
        this.activator = activator;
        status.setWrapText(true);
        activeLine.setWrapText(true);
        details.setEditable(false);
        details.setPrefRowCount(8);
        list.setPlaceholder(new Label("No profiles yet. Press New, or Save current setup to capture what's on PATH now."));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Profile profile, boolean empty) {
                super.updateItem(profile, empty);
                setText(empty || profile == null ? null : row(profile));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, profile) -> {
            enableButtons();
            showDetails(profile);
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        create.setOnAction(event -> openForm(null, null, List.of()));
        saveCurrent.setOnAction(event -> saveCurrent());
        edit.setOnAction(event -> selected().ifPresent(profile -> openForm(profile, profile.name(), List.of())));
        delete.setOnAction(event -> selected().ifPresent(this::deleteProfile));
        activate.setOnAction(event -> selected().ifPresent(this::activate));
        HBox actions = new HBox(Spacing.GAP, create, saveCurrent, edit, delete, activate, refresh, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        setTop(new VBox(Spacing.GAP, actions, activeLine));
        setCenter(list);
        setBottom(details);
        Spacing.page(this);
        enableButtons();
        activator.onChange(this::refresh);
        refresh();
    }

    public void refresh() {
        worker.execute(() -> {
            ProfileState next = ProfileState.read(context, store);
            Platform.runLater(() -> show(next));
        });
    }

    private void show(ProfileState next) {
        snapshot = next;
        Profile before = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(next.profiles());
        if (before != null) {
            next.profiles().stream().filter(profile -> profile.name().equalsIgnoreCase(before.name())).findFirst()
                    .ifPresent(profile -> list.getSelectionModel().select(profile));
        }
        if (next.error() != null) {
            activeLine.setText(next.error() + " Fix or remove profiles.json, then press Refresh. Nothing will be saved until then.");
        } else if (next.active().isPresent()) {
            activeLine.setText("Active: " + next.active().get());
        } else {
            activeLine.setText(next.profiles().isEmpty() ? "" : "No profile matches the current setup.");
        }
        enableButtons();
        showDetails(list.getSelectionModel().getSelectedItem());
    }

    private String row(Profile profile) {
        StringBuilder text = new StringBuilder(profile.name()).append("   ").append(ProfileResolver.describe(profile));
        if (snapshot.active().isPresent() && snapshot.active().get().equalsIgnoreCase(profile.name())) {
            text.append("   [active]");
        }
        List<String> missing = snapshot.missing().getOrDefault(profile.name(), List.of());
        if (!missing.isEmpty()) {
            text.append("   [missing: ").append(String.join(", ", missing)).append(']');
        }
        return text.toString();
    }

    private void enableButtons() {
        boolean broken = snapshot.error() != null;
        boolean none = list.getSelectionModel().getSelectedItem() == null;
        create.setDisable(broken);
        saveCurrent.setDisable(broken);
        edit.setDisable(broken || none);
        delete.setDisable(broken || none);
        activate.setDisable(broken || none);
    }

    private void showDetails(Profile profile) {
        if (profile == null) {
            details.setText("");
            return;
        }
        try {
            ProfileResolver.Resolved resolved = resolve(profile);
            StringBuilder text = new StringBuilder();
            text.append(slotLine(resolved.jdk())).append('\n');
            for (ProfileResolver.Slot slot : resolved.tools().values()) {
                text.append(slotLine(slot)).append('\n');
            }
            text.append("\nPATH after activating:\n");
            for (String entry : activator.plan(profile, resolved, snapshot.userPath()).path().split(";")) {
                text.append("  ").append(entry).append('\n');
            }
            details.setText(text.toString());
        } catch (RuntimeException ex) {
            details.setText(ex.getMessage());
        }
    }

    private static String slotLine(ProfileResolver.Slot slot) {
        return slot.label() + "   " + slot.folder() + (slot.installed() ? "" : "   (not installed)");
    }

    private Optional<Profile> selected() {
        return Optional.ofNullable(list.getSelectionModel().getSelectedItem());
    }

    private ProfileResolver.Resolved resolve(Profile profile) {
        return ProfileResolver.resolve(profile, context.root());
    }

    private void saveCurrent() {
        CurrentSetup.Captured captured = CurrentSetup.capture(snapshot.userVariables(), snapshot.managed(), context.root());
        Profile draft = new Profile("", captured.jdk(), captured.versions().get(Tool.MAVEN), captured.versions().get(Tool.ANT),
                captured.versions().get(Tool.GRADLE));
        openForm(draft, null, captured.notes());
    }

    private void openForm(Profile initial, String originalName, List<String> notes) {
        List<Profile> existing = snapshot.profiles();
        ProfileForm.show(this, initial, originalName, existing, snapshot.managed(), snapshot.installed(), notes).ifPresent(profile ->
                worker.execute(() -> {
                    String message;
                    try {
                        store.save(store.read(), profile, originalName);
                        message = (originalName == null ? "Saved " : "Updated ") + profile.name();
                    } catch (IOException ex) {
                        message = ex.getMessage();
                    }
                    String text = message;
                    Platform.runLater(() -> {
                        status.setText(text);
                        activator.changed();
                    });
                }));
    }

    private void deleteProfile(Profile profile) {
        if (!ConfirmDialog.confirm(this, Confirmations.deleteProfile(profile.name()))) {
            return;
        }
        worker.execute(() -> {
            String message;
            try {
                store.delete(store.read(), profile.name());
                message = "Deleted " + profile.name();
            } catch (IOException ex) {
                message = ex.getMessage();
            }
            String text = message;
            Platform.runLater(() -> {
                status.setText(text);
                activator.changed();
            });
        });
    }

    private void activate(Profile profile) {
        activator.activate(this, profile, snapshot.userPath(), status::setText);
    }
}
