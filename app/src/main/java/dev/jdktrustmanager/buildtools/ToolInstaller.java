package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.install.ZipInstaller;
import dev.jdktrustmanager.model.ToolInstallation;
import dev.jdktrustmanager.model.ToolSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Downloads, verifies, unpacks, and places one tool version beside the others. */
public final class ToolInstaller {

    /** Streams a download to a file. Production uses {@link ZipInstaller#download}; tests supply bytes directly. */
    @FunctionalInterface
    public interface Downloader {
        void download(String url, Path target, ZipInstaller.InstallProgress progress) throws IOException;
    }

    private final Downloader downloader;
    private final ToolVerifier verifier;
    private final Path zipCache;

    public ToolInstaller(ToolVerifier verifier, Path zipCache) {
        this((url, target, progress) -> new ZipInstaller().download(url, target, -1, progress), verifier, zipCache);
    }

    public ToolInstaller(Downloader downloader, ToolVerifier verifier, Path zipCache) {
        this.downloader = downloader;
        this.verifier = verifier;
        this.zipCache = zipCache;
    }

    public ToolInstallation install(BuildTool tool, ToolRelease release, Path root, Optional<Path> javaForRunCheck,
            ZipInstaller.InstallProgress progress) throws IOException {
        Path destination = ToolLayout.destination(root, tool, release.version());
        if (Files.exists(destination)) {
            throw new IOException(tool.displayName() + " " + release.version() + " is already installed");
        }
        ToolHttp.requireHttps(release.zipUrl());
        Files.createDirectories(zipCache);
        Path zip = zipCache.resolve(tool.folderPrefix() + "-" + release.version() + ".zip");
        ToolVerifier.Outcome outcome = null;
        if (Files.isRegularFile(zip)) {
            try {
                outcome = verifier.verify(tool, release, zip);
                note(progress, "Using cached zip " + zip.getFileName());
            } catch (VerificationException stale) {
                note(progress, "Cached zip failed its check; downloading again");
            }
        }
        if (outcome == null) {
            note(progress, "GET " + release.zipUrl());
            downloader.download(release.zipUrl(), zip, progress);
            phase(progress, "Verifying " + zip.getFileName());
            outcome = verifier.verify(tool, release, zip);
        }
        note(progress, outcome.message());
        Path folder = ToolLayout.folder(root);
        Files.createDirectories(folder);
        Path unpacked = folder.resolve(".unpack-" + System.currentTimeMillis());
        try {
            phase(progress, "Unpacking " + zip.getFileName());
            ZipInstaller.unzip(zip, unpacked);
            Path home = findHome(tool, unpacked);
            phase(progress, "Moving to " + destination);
            Files.move(home, destination);
            if (javaForRunCheck.isPresent()) {
                runCheck(tool, release, destination, javaForRunCheck.get(), progress);
            } else {
                note(progress, "No JDK in " + tool.javaRange(release.version()).describe() + " is installed; skipped the run check");
            }
        } finally {
            ZipInstaller.deleteRecursively(unpacked);
        }
        return new ToolInstallation(tool.tool(), release.version(), destination.toString(), ToolSource.DOWNLOADED,
                outcome.used(), Instant.now(), false, false, "");
    }

    /** Deletes an installed version. The cached zip is kept so a reinstall doesn't download again. */
    public void uninstall(ToolInstallation installation, Path root) throws IOException {
        if (installation.active()) {
            throw new IOException(installation.version() + " is on PATH. Set another version or remove it from PATH first.");
        }
        if (installation.pinned()) {
            throw new IOException(installation.version() + " is pinned. Unpin it first.");
        }
        Path home = Path.of(installation.path()).toAbsolutePath().normalize();
        Path folder = ToolLayout.folder(root).toAbsolutePath().normalize();
        if (home.getParent() == null || !home.getParent().equals(folder)) {
            throw new IOException("Refusing to delete a folder outside " + folder + ": " + home);
        }
        ZipInstaller.deleteRecursively(home);
    }

    private static void runCheck(BuildTool tool, ToolRelease release, Path destination, Path java,
            ZipInstaller.InstallProgress progress) throws IOException {
        try {
            String output = RunCheck.run(tool, destination, java);
            if (!output.contains(release.version())) {
                throw new IOException("reported a different version: " + output.strip());
            }
            note(progress, output.strip().lines().findFirst().orElse(""));
        } catch (IOException ex) {
            ZipInstaller.deleteRecursively(destination);
            throw new IOException(tool.displayName() + " " + release.version() + " didn't run with " + java + ": " + ex.getMessage(), ex);
        }
    }

    /** The single folder in the zip that contains the tool's launcher. */
    private static Path findHome(BuildTool tool, Path unpacked) throws IOException {
        List<Path> homes = new ArrayList<>();
        try (var children = Files.list(unpacked)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child) && ToolLayout.launcher(tool, child) != null) {
                    homes.add(child);
                }
            }
        }
        if (homes.size() != 1) {
            throw new IOException("Expected one " + tool.displayName() + " folder with " + tool.launchers().get(0)
                    + " in the zip, found " + homes.size());
        }
        return homes.get(0);
    }

    private static void note(ZipInstaller.InstallProgress progress, String line) {
        if (progress != null) {
            progress.log(line);
        }
    }

    private static void phase(ZipInstaller.InstallProgress progress, String phase) {
        if (progress != null) {
            progress.phase(phase);
        }
    }
}
