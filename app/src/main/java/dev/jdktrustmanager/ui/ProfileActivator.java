package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.buildtools.BuildTool;
import dev.jdktrustmanager.buildtools.BuildTools;
import dev.jdktrustmanager.buildtools.ToolRelease;
import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.env.EnvironmentPlan;
import dev.jdktrustmanager.env.UserPathWriter;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.model.Distribution;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Profile;
import dev.jdktrustmanager.model.ToolInstallations;
import dev.jdktrustmanager.profiles.JdkSource;
import dev.jdktrustmanager.profiles.ProfileActivation;
import dev.jdktrustmanager.profiles.ProfileResolver;
import dev.jdktrustmanager.profiles.ProfileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;

/** Activating a profile: resolve, offer missing installs, confirm, then queue installs and the one write. Shared by every view. */
public final class ProfileActivator {

    private final ToolContext context;
    private final ProfileStore store;
    private final CatalogBrowser browser;
    private final DiscoClient client;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-profiles");
        thread.setDaemon(true);
        return thread;
    });

    public ProfileActivator(ToolContext context, ProfileStore store, CatalogBrowser browser, DiscoClient client) {
        this.context = context;
        this.store = store;
        this.browser = browser;
        this.client = client;
    }

    /** Runs every time profiles, installs, or the active setup may have changed; always on the FX thread. */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public void changed() {
        Platform.runLater(() -> listeners.forEach(Runnable::run));
    }

    /** Resolve, offer to install what's missing, confirm the change list, then queue installs and the one write. */
    public void activate(Node owner, Profile profile, String userPath, Consumer<String> status) {
        ProfileResolver.Resolved resolved = resolve(profile);
        if (resolved.missing().isEmpty()) {
            confirmAndActivate(owner, profile, resolved, List.of(), userPath, status);
            return;
        }
        status.accept("Looking up the missing versions…");
        worker.execute(() -> {
            List<WorkQueue.Job> installs = new ArrayList<>();
            List<String> cannot = new ArrayList<>();
            for (ProfileResolver.Slot slot : resolved.missing()) {
                Optional<WorkQueue.Job> job = slot == resolved.jdk() ? jdkInstall(profile) : toolInstall(profile, slot);
                job.ifPresentOrElse(installs::add, () -> cannot.add(slot.label()));
            }
            Platform.runLater(() -> {
                status.accept("");
                var prompt = Confirmations.installMissing(profile.name(), labels(resolved.missing()), cannot);
                if (!ConfirmDialog.confirm(owner, prompt)) {
                    return;
                }
                if (!cannot.isEmpty()) {
                    installs.forEach(context.queue()::submit);
                    status.accept("Installing what can be installed. Activate " + profile.name() + " once the rest is installed.");
                    return;
                }
                confirmAndActivate(owner, profile, resolved, installs, userPath, status);
            });
        });
    }

    private void confirmAndActivate(Node owner, Profile profile, ProfileResolver.Resolved resolved, List<WorkQueue.Job> installsFirst,
            String userPath, Consumer<String> status) {
        String preview = plan(profile, resolved, userPath).path();
        var prompt = Confirmations.activateProfile(profile.name(), ProfileActivation.changes(resolved),
                ProfileActivation.javaWarnings(profile),
                ProfileActivation.leftoverEntries(resolved, userPath, context.root().toString()), preview);
        if (!ConfirmDialog.confirm(owner, prompt)) {
            return;
        }
        installsFirst.forEach(context.queue()::submit);
        context.queue().submit(activationJob(profile, status));
        status.accept(installsFirst.isEmpty() ? "Activating " + profile.name() + "…" : "Installing, then activating " + profile.name() + "…");
    }

    /** Runs after any installs: checks every slot again and writes nothing if one is still missing. */
    private WorkQueue.Job activationJob(Profile profile, Consumer<String> status) {
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
                    changed();
                    Platform.runLater(() -> status.accept(message));
                } catch (Exception ex) {
                    changed();
                    Platform.runLater(() -> status.accept(ex.getMessage()));
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
            return Optional.of(new InstallJob(card, client, context.settings(), context.queue(), context.stores(), this::changed));
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
                return release.map(item -> ToolVersionsPane.installJob(tool, item, context, message -> changed()));
            } catch (IOException | RuntimeException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    EnvironmentPlan plan(Profile profile, ProfileResolver.Resolved resolved, String currentPath) {
        Map<BuildTool, String> homes = new HashMap<>();
        for (BuildTool tool : BuildTools.ALL) {
            ProfileResolver.Slot slot = resolved.tools().get(tool.tool());
            homes.put(tool, slot == null ? null : slot.folder().toString());
        }
        return EnvironmentPlan.forProfile(currentPath, context.root().toString(), resolved.jdk().folder().toString(), homes);
    }

    private ProfileResolver.Resolved resolve(Profile profile) {
        return ProfileResolver.resolve(profile, context.root());
    }

    static List<String> labels(List<ProfileResolver.Slot> slots) {
        return slots.stream().map(ProfileResolver.Slot::label).toList();
    }
}
