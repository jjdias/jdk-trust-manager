package dev.jdktrustmanager.install;

import dev.jdktrustmanager.model.Installation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Uninstaller {

    public record Result(List<Installation> remaining, boolean clearedActiveHome) {
    }

    public interface ActiveJava {
        String javaHome();

        void clear();
    }

    public Result uninstall(Installation installation, Path root, Path bundledRuntime, Path zipCache, ActiveJava active)
            throws IOException {
        Path home = Path.of(installation.path());
        Layout.checkInsideRoot(root, home, bundledRuntime);
        boolean cleared = false;
        if (active != null && active.javaHome() != null && Path.of(active.javaHome()).toAbsolutePath().normalize().equals(home.toAbsolutePath().normalize())) {
            active.clear();
            cleared = true;
        }
        if (Files.exists(home)) {
            ZipInstaller.deleteRecursively(home);
        }
        if (zipCache != null && installation.zipSha256() != null && !installation.zipSha256().isBlank()) {
            Path zip = zipCache.resolve(installation.zipSha256() + ".zip");
            if (!Files.isRegularFile(zip)) {
                throw new IOException("Cached zip is missing: " + zip);
            }
        }
        List<Installation> remaining = new ArrayList<>();
        return new Result(remaining, cleared);
    }
}
