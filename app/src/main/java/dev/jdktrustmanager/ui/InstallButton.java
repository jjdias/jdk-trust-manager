package dev.jdktrustmanager.ui;

import dev.jdktrustmanager.model.Installations;

/** What the Catalog's install button says and does for a package, given what is already in its folder. */
final class InstallButton {

    /** {@code update} means an older patch is installed and this replaces it in place. */
    record State(String label, boolean enabled, boolean update) {
    }

    private InstallButton() {
    }

    /**
     * {@code installedVersion} is the JAVA_VERSION in the destination's release file, or null/blank when the folder is
     * empty, missing, or not a JDK (then it's simply installed over). A newer local version also counts as installed.
     */
    static State state(String installedVersion, String catalogVersion) {
        if (installedVersion == null || installedVersion.isBlank()) {
            return new State("Install", true, false);
        }
        if (Installations.newer(catalogVersion, installedVersion)) {
            return new State("Update", true, true);
        }
        return new State("Installed", false, false);
    }
}
