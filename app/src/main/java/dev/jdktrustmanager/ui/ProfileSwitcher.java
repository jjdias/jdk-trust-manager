package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.profiles.ProfileStore;
import dev.jdktrustmanager.profiles.SwitcherModel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/** Top-bar menu: shows the active profile and switches through the same confirmed activation as the Profiles tab. */
public final class ProfileSwitcher extends HBox {

    private static final String EMPTY = "No profiles yet. Create one on the Profiles tab.";

    private final ProfileActivator activator;
    private final ToolContext context;
    private final ProfileStore store;
    private final MenuButton button = new MenuButton(SwitcherModel.title(Optional.empty(), false));
    private final Label status = new Label();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-profile-switcher");
        thread.setDaemon(true);
        return thread;
    });
    private ProfileState state = ProfileState.empty();

    ProfileSwitcher(ProfileActivator activator, ToolContext context, ProfileStore store) {
        super(Spacing.GAP);
        this.activator = activator;
        this.context = context;
        this.store = store;
        setAlignment(Pos.CENTER_LEFT);
        status.setMaxWidth(320);
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        status.textProperty().addListener((observable, before, text) -> status.setTooltip(
                text == null || text.isBlank() ? null : new Tooltip(text)));
        getChildren().addAll(button, status);
        activator.onChange(this::refresh);
        refresh();
    }

    /** Re-reads the profiles and the active setup off the FX thread, then updates the menu on it. */
    public void refresh() {
        worker.execute(() -> {
            ProfileState read = ProfileState.read(context, store);
            Platform.runLater(() -> apply(read));
        });
    }

    private void apply(ProfileState read) {
        state = read;
        boolean unavailable = read.error() != null;
        button.setText(SwitcherModel.title(read.active(), unavailable));
        button.setDisable(unavailable);
        button.setTooltip(unavailable ? new Tooltip(read.error()) : null);
        button.getItems().setAll(menuItems(read, unavailable));
    }

    private List<MenuItem> menuItems(ProfileState read, boolean unavailable) {
        if (unavailable) {
            return List.of();
        }
        var items = SwitcherModel.items(read.profiles(), read.missing());
        if (items.isEmpty()) {
            MenuItem none = new MenuItem(EMPTY);
            none.setDisable(true);
            return List.of(none);
        }
        var current = SwitcherModel.selected(items, read.active());
        return items.stream().map(item -> {
            boolean isActive = current.isPresent() && current.get().equals(item);
            MenuItem menuItem = new MenuItem(isActive ? item.label() + "   (active)" : item.label());
            menuItem.setOnAction(event -> Platform.runLater(() -> pick(item)));
            return menuItem;
        }).toList();
    }

    private void pick(SwitcherModel.Item item) {
        if (!SwitcherModel.shouldActivate(item, state.active())) {
            return;
        }
        Profile profile = state.profiles().stream().filter(p -> p.name().equals(item.name())).findFirst().orElse(null);
        if (profile != null) {
            activator.activate(this, profile, state.userPath(), status::setText);
        }
    }
}
