package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.profiles.ProfileStore;
import dev.jdktrustmanager.profiles.SwitcherModel;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;

/** Top-bar drop-down: shows the active profile and switches through the same confirmed activation as the Profiles tab. */
public final class ProfileSwitcher extends HBox {

    private final ProfileActivator activator;
    private final ToolContext context;
    private final ProfileStore store;
    private final ComboBox<SwitcherModel.Item> combo = new ComboBox<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-profile-switcher");
        thread.setDaemon(true);
        return thread;
    });
    private ProfileState state = ProfileState.empty();
    private boolean updating;

    ProfileSwitcher(ProfileActivator activator, ToolContext context, ProfileStore store) {
        super(Spacing.GAP);
        this.activator = activator;
        this.context = context;
        this.store = store;
        setAlignment(Pos.CENTER_LEFT);
        combo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SwitcherModel.Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        combo.setButtonCell(buttonCell(SwitcherModel.title(Optional.empty(), false)));
        combo.valueProperty().addListener((observable, before, picked) -> {
            if (updating) {
                return;
            }
            if (SwitcherModel.shouldActivate(picked, state.active())) {
                Profile profile = state.profiles().stream().filter(p -> p.name().equals(picked.name())).findFirst().orElse(null);
                if (profile != null) {
                    activator.activate(this, profile, state.userPath(), status -> {});
                }
            }
            refresh();
        });
        getChildren().add(combo);
        activator.onChange(this::refresh);
        refresh();
    }

    /** Re-reads the profiles and the active setup off the FX thread, then updates the drop-down on it. */
    public void refresh() {
        worker.execute(() -> {
            ProfileState read = ProfileState.read(context, store);
            Platform.runLater(() -> apply(read));
        });
    }

    private void apply(ProfileState read) {
        state = read;
        var items = SwitcherModel.items(read.profiles(), read.missing());
        boolean unavailable = read.error() != null;
        updating = true;
        try {
            combo.getItems().setAll(items);
            combo.setButtonCell(buttonCell(SwitcherModel.title(read.active(), unavailable)));
            combo.setValue(SwitcherModel.selected(items, read.active()).orElse(null));
            combo.setDisable(unavailable);
        } finally {
            updating = false;
        }
    }

    private static ListCell<SwitcherModel.Item> buttonCell(String title) {
        return new ListCell<>() {
            @Override
            protected void updateItem(SwitcherModel.Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(title);
            }
        };
    }
}
