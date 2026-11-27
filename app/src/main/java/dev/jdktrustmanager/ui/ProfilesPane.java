package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.ToolLayout;
import dev.jdktrustmanager.buildtools.ToolRelease;
import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.env.EnvironmentPlan;
import dev.jdktrustmanager.env.JavaVariables;
import dev.jdktrustmanager.env.RegistryEnvironment;
import dev.jdktrustmanager.env.UserPathWriter;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolInstallations;
import dev.jdktrustmanager.profiles.CurrentSetup;
import dev.jdktrustmanager.profiles.JdkSource;
import dev.jdktrustmanager.profiles.ProfileActivation;
import dev.jdktrustmanager.profiles.ProfileMatch;
import dev.jdktrustmanager.profiles.ProfileResolver;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Everything a refresh reads, gathered off the FX thread. */
    private record Snapshot(List<Profile> profiles, String error, Optional<String> active, Map<String, List<String>> missing,
            List<Installation> managed, Map<Tool, List<String>> installed, Map<String, String> userVariables, String userPath) {
    }

    private final ToolContext context;
    private final ProfileStore store;
    private final CatalogBrowser browser;
    private final DiscoClient client;
    private final ListView<Profile> list = new ListView<>();
    private final Label activeLine = new Label();
    private final Label status = new Label();
    private final TextArea details = new TextArea();
    private final Button create = new Button("New");
    private final Button saveCurrent = new Button("Save current setup");
    private final Button edit = new Button("Edit");
    private final Button delete = new Button("Delete");
    private final Button activate = new Button("Activate");
    private Snapshot snapshot = new Snapshot(List.of(), null, Optional.empty(), Map.of(), List.of(), Map.of(), Map.of(), "");
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-profiles");
        thread.setDaemon(true);
        return thread;
    });

    public ProfilesPane(ToolContext context, ProfileStore store, CatalogBrowser browser, DiscoClient client) {
        this.context = context;
        this.store = store;
        this.browser = browser;
        this.client = client;
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
        refresh();
    }

    public void refresh() {
        worker.execute(() -> {
            Snapshot next = read();
            Platform.runLater(() -> show(next));
        });
    }

    private Snapshot read() {
        Path root = context.root();
        Map<String, String> user = Map.of();
        try {
            user = RegistryEnvironment.readAll(RegistryEnvironment.USER).orElse(Map.of());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        Map<Tool, List<String>> installed = new EnumMap<>(Tool.class);
        for (BuildTool tool : BuildTools.ALL) {
            try {
                installed.put(tool.tool(), ToolLayout.installed(root, tool).stream().map(home -> ToolLayout.versionOf(tool, home)).toList());
            } catch (IOException ex) {
                installed.put(tool.tool(), List.of());
            }
        }
        List<Installation> managed = context.managedJdks();
        String userPath = JavaVariables.find(user, "Path");
        try {
            List<Profile> profiles = store.read();
            Map<String, List<String>> missing = new HashMap<>();
            for (Profile profile : profiles) {
                missing.put(profile.name(), labels(resolve(profile).missing()));
            }
            return new Snapshot(profiles, null, ProfileMatch.active(profiles, user, root), missing, managed, installed, user, userPath);
        } catch (IOException | RuntimeException ex) {
            return new Snapshot(List.of(), ex.getMessage(), Optional.empty(), Map.of(), managed, installed, user, userPath);
        }
    }

    private void show(Snapshot next) {
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
            for (String entry : plan(profile, resolved, snapshot.userPath()).path().split(";")) {
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

    private EnvironmentPlan plan(Profile profile, ProfileResolver.Resolved resolved, String currentPath) {
        Map<BuildTool, String> homes = new HashMap<>();
        for (BuildTool tool : BuildTools.ALL) {
            ProfileResolver.Slot slot = resolved.tools().get(tool.tool());
            homes.put(tool, slot == null ? null : slot.folder().toString());
        }
        return EnvironmentPlan.forProfile(currentPath, context.root().toString(), resolved.jdk().folder().toString(), homes);
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
                    Snapshot next = read();
                    Platform.runLater(() -> {
                        show(next);
                        status.setText(text);
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
            Snapshot next = read();
            Platform.runLater(() -> {
                show(next);
                status.setText(text);
            });
        });
    }

    /** Resolve, offer to install what's missing, confirm the change list, then queue installs and the one write. */
    private void activate(Profile profile) {
        ProfileResolver.Resolved resolved = resolve(profile);
        if (resolved.missing().isEmpty()) {
            confirmAndActivate(profile, resolved, List.of());
            return;
        }
        status.setText("Looking up the missing versions…");
        worker.execute(() -> {
            List<WorkQueue.Job> installs = new ArrayList<>();
            List<String> cannot = new ArrayList<>();
            for (ProfileResolver.Slot slot : resolved.missing()) {
                Optional<WorkQueue.Job> job = slot == resolved.jdk() ? jdkInstall(profile) : toolInstall(profile, slot);
                job.ifPresentOrElse(installs::add, () -> cannot.add(slot.label()));
            }
            Platform.runLater(() -> {
                status.setText("");
                var prompt = Confirmations.installMissing(profile.name(), labels(resolved.missing()), cannot);
                if (!ConfirmDialog.confirm(this, prompt)) {
                    return;
                }
                if (!cannot.isEmpty()) {
                    installs.forEach(context.queue()::submit);
                    status.setText("Installing what can be installed. Activate " + profile.name() + " once the rest is installed.");
                    return;
                }
                confirmAndActivate(profile, resolved, installs);
            });
        });
    }

    private void confirmAndActivate(Profile profile, ProfileResolver.Resolved resolved, List<WorkQueue.Job> installsFirst) {
        String preview = plan(profile, resolved, snapshot.userPath()).path();
        var prompt = Confirmations.activateProfile(profile.name(), ProfileActivation.changes(resolved),
                ProfileActivation.javaWarnings(profile), preview);
        if (!ConfirmDialog.confirm(this, prompt)) {
            return;
        }
        installsFirst.forEach(context.queue()::submit);
        context.queue().submit(activationJob(profile));
        status.setText(installsFirst.isEmpty() ? "Activating " + profile.name() + "…" : "Installing, then activating " + profile.name() + "…");
    }

    /** Runs after any installs: checks every slot again and writes nothing if one is still missing. */
    private WorkQueue.Job activationJob(Profile profile) {
        return new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.ADOPT;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                try {
                    ProfileResolver.Resolved again = resolve(profile);
                    Optional<String> blocked = ProfileActivation.readyToWrite(again);
                    if (blocked.isPresent()) {
                        throw new IOException(blocked.get());
                    }
                    UserPathWriter.live().write(current -> plan(profile, again, current));
                    String jdkHome = again.jdk().folder().toString();
                    if (Files.exists(context.installationsFile())) {
                        context.stores().writeInstallations(context.installationsFile(),
                                Installations.markActive(context.stores().readInstallations(context.installationsFile()), jdkHome));
                    }
                    context.update(all -> {
                        List<dev.jdktrustmanager.model.ToolInstallation> next = all;
                        for (BuildTool tool : BuildTools.ALL) {
                            ProfileResolver.Slot slot = again.tools().get(tool.tool());
                            next = ToolInstallations.markActive(next, tool.tool(), slot == null ? null : slot.folder().toString());
                        }
                        return next;
                    });
                    String message = "Activated " + profile.name() + ". Open a new terminal to use it.";
                    progress.log(message);
                    Snapshot next = read();
                    Platform.runLater(() -> {
                        show(next);
                        status.setText(message);
                    });
                } catch (Exception ex) {
                    Snapshot next = read();
                    Platform.runLater(() -> {
                        show(next);
                        status.setText(ex.getMessage());
                    });
                    throw ex;
                }
            }
        };
    }

    /** An install job for the profile's JDK, or empty when the catalog doesn't offer that vendor and major. */
    private Optional<WorkQueue.Job> jdkInstall(Profile profile) {
        try {
            var settings = context.settings().get();
            Optional<Distribution> vendor = JdkSource.find(browser.vendors(settings, false), profile.jdk());
            if (vendor.isEmpty()) {
                return Optional.empty();
            }
            CatalogBrowser.Card card = browser.latest(vendor.get(), profile.jdk().major(), settings, JdkSource.javafx(profile.jdk()), false);
            if (card.pkg() == null) {
                return Optional.empty();
            }
            return Optional.of(new InstallJob(card, client, context.settings(), context.queue(), context.stores(), this::refresh));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** An install job for a missing tool version, or empty when the version list doesn't have it. */
    private Optional<WorkQueue.Job> toolInstall(Profile profile, ProfileResolver.Slot slot) {
        for (BuildTool tool : BuildTools.ALL) {
            String version = profile.version(tool.tool());
            ProfileResolver.Slot toolSlot = resolve(profile).tools().get(tool.tool());
            if (version == null || toolSlot == null || !toolSlot.label().equals(slot.label())) {
                continue;
            }
            try {
                Optional<ToolRelease> release = context.catalog().releases(tool, context.settings().get().catalogMaxAgeHours(), false)
                        .value().stream().filter(item -> item.version().equals(version)).findFirst();
                return release.map(item -> ToolVersionsPane.installJob(tool, item, context, message -> refresh()));
            } catch (IOException | RuntimeException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<String> labels(List<ProfileResolver.Slot> slots) {
        return slots.stream().map(ProfileResolver.Slot::label).toList();
    }
}
