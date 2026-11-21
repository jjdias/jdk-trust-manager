package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.catalog.CatalogBrowser;
import dev.jdktrustmanager.catalog.DiscoClient;
import dev.jdktrustmanager.install.WorkQueue;
import dev.jdktrustmanager.install.ZipInstaller;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.model.Installations;
import dev.jdktrustmanager.model.Settings;
import dev.jdktrustmanager.persist.AppFiles;
import dev.jdktrustmanager.persist.JsonStores;
import dev.jdktrustmanager.trust.TrustSync;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Downloads and installs one catalog package. Used by Install in the Catalog and Update on the Installed screen. */
final class InstallJob implements WorkQueue.Job {

    private final CatalogBrowser.Card card;
    private final DiscoClient client;
    private final Supplier<Settings> settings;
    private final WorkQueue queue;
    private final JsonStores stores;
    private final Runnable after;

    InstallJob(CatalogBrowser.Card card, DiscoClient client, Supplier<Settings> settings, WorkQueue queue,
            JsonStores stores, Runnable after) {
        this.card = card;
        this.client = client;
        this.settings = settings;
        this.queue = queue;
        this.stores = stores;
        this.after = after;
    }

    @Override
    public WorkQueue.Kind kind() {
        return WorkQueue.Kind.INSTALL;
    }

    @Override
    public void run(WorkQueue.Progress progress) throws Exception {
        progress.log("Install " + card.vendor() + " " + card.major() + " " + card.pkg().javaVersion());
        progress.state(WorkQueue.State.DOWNLOADING);
        if (progress.cancelled()) {
            throw new ZipInstaller.CancelledException();
        }
        var packageDetail = client.packageDetail(card.pkg().id());
        Path root = Path.of(settings.get().rootDirectory());
        Path runtimeHome = Path.of(System.getProperty("java.home"));
        Path bundled = runtimeHome.startsWith(root) ? null : runtimeHome;
        Installation installed = new ZipInstaller().install(card.pkg(), packageDetail, root, AppFiles.zipCache(), bundled, home -> {
            progress.state(WorkQueue.State.UPDATING_TRUSTSTORE);
            progress.log("Replaying trusted certificates");
            try {
                new TrustSync().syncHomes(root, List.of(home), bundled, progress::log);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            }
        }, new ZipInstaller.InstallProgress() {
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
        });
        remember(installed);
        progress.log("Installed " + installed.path());
        if (after != null) {
            javafx.application.Platform.runLater(after);
        }
    }

    private void remember(Installation installed) throws IOException {
        Path file = AppFiles.installations();
        List<Installation> known = Files.exists(file) ? new ArrayList<>(stores.readInstallations(file)) : new ArrayList<>();
        Installation previous = known.stream()
                .filter(item -> Installations.samePath(item.path(), installed.path()))
                .findFirst()
                .orElse(null);
        known.removeIf(item -> Installations.samePath(item.path(), installed.path()));
        known.add(Installations.keepUserFlags(previous, installed));
        stores.writeInstallations(file, known);
    }
}
