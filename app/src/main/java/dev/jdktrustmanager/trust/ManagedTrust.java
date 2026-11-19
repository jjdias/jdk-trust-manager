package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.install.JdkScanner;
import dev.jdktrustmanager.model.Installation;
import dev.jdktrustmanager.persist.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ManagedTrust {

    private final JsonStores stores = new JsonStores();

    public List<Path> homes(Path root, Path installationsFile) throws IOException {
        List<Installation> known = Files.isRegularFile(installationsFile)
                ? stores.readInstallations(installationsFile)
                : List.of();
        Set<Path> homes = new LinkedHashSet<>();
        for (Installation installation : known) {
            if (installation.path() != null && !installation.path().isBlank()) {
                homes.add(Path.of(installation.path()));
            }
        }
        for (JdkScanner.Discovered found : new JdkScanner().scan(root, List.of(), known, List.of())) {
            homes.add(found.path());
        }
        return new ArrayList<>(homes);
    }

    public void sync(Path root, Path installationsFile, Path bundledRuntime, Consumer<String> log) throws IOException, InterruptedException {
        new TrustSync().syncHomes(root, homes(root, installationsFile), bundledRuntime, log);
    }
}
