package dev.jdktrustmanager.ui;

/** Wording for the prompts shown before actions that change files or the user environment. */
public final class Confirmations {

    /** A confirmation prompt: window title, explanation, and the label of the button that goes ahead. */
    public record Prompt(String title, String message, String action) {
    }

    private Confirmations() {
    }

    public static Prompt removeJdk(String home) {
        return new Prompt("Remove JDK",
                "Delete " + home + " and everything in it?\n\nThis can't be undone. You can install the version again from the Catalog.",
                "Remove");
    }

    public static Prompt setJavaHome(String home, boolean removeOtherJavaEntries) {
        String message = "Set the user JAVA_HOME to " + home + " and update the user PATH?";
        if (removeOtherJavaEntries) {
            message += "\n\nOther Java entries will be removed from the user PATH.";
        }
        message += "\n\nOpen a new terminal afterwards to pick up the change.";
        return new Prompt("Set JAVA_HOME", message, "Set JAVA_HOME");
    }

    public static Prompt adopt(String source, String destination, boolean inPlace) {
        if (inPlace) {
            return new Prompt("Adopt JDK",
                    "Manage " + source + " with this app?\n\nNothing is moved. Trusted certificates are added to its cacerts, with a backup first.",
                    "Adopt");
        }
        return new Prompt("Adopt JDK",
                "Copy " + source + " to " + destination + " and manage the copy?\n\nThe original stays where it is. Trusted certificates are added to the copy's cacerts.",
                "Copy and adopt");
    }

    public static Prompt update(String home, String currentVersion, String newVersion) {
        return new Prompt("Update JDK",
                "Replace " + currentVersion + " in " + home + " with " + newVersion + "?\n\n"
                        + "The folder stays the same, so JAVA_HOME and PATH keep working. If the install fails, the old version is put back. "
                        + "Close programs running from this JDK first.",
                "Update");
    }

    public static Prompt installTool(String name, String version, String folder, boolean signed) {
        String check = signed
                ? "The download is checked against Apache's signing keys before anything is installed."
                : "The download is checked by SHA-256 before anything is installed.";
        return new Prompt("Install " + name,
                "Install " + name + " " + version + " into " + folder + "?\n\n" + check
                        + " Other versions stay as they are, and nothing changes on PATH until you choose Set to path.",
                "Install");
    }

    public static Prompt setToolPath(String name, String version, String javaWarningOrNull) {
        String message = "Put " + name + " " + version + " on the user PATH and set its home variables?\n\n"
                + "JAVA_HOME and the other build tools stay as they are. Open a new terminal afterwards to pick up the change.";
        if (javaWarningOrNull != null && !javaWarningOrNull.isBlank()) {
            message = javaWarningOrNull + "\n\n" + message;
        }
        return new Prompt("Set " + name + " on PATH", message, "Set to path");
    }

    public static Prompt removeTool(String name, String version, String folder) {
        return new Prompt("Remove " + name,
                "Delete " + name + " " + version + " (" + folder + ")?\n\nThis can't be undone. The downloaded zip is kept, so reinstalling is quick.",
                "Remove");
    }

    public static Prompt removeToolFromPath(String name, java.util.List<String> variables) {
        return new Prompt("Remove " + name + " from PATH",
                "Take " + name + " off the user PATH and delete " + String.join(" and ", variables) + "?\n\n"
                        + "Installed versions stay on disk. Open a new terminal afterwards to pick up the change.",
                "Remove from PATH");
    }

    public static Prompt removeJavaFromPath() {
        return new Prompt("Remove Java from PATH",
                "Delete the user JAVA_HOME and take %JAVA_HOME%\\bin off the user PATH?\n\n"
                        + "Installed JDKs stay on disk, and Maven, Ant, and Gradle stay on PATH. "
                        + "A JAVA_HOME set for the whole machine, if any, takes over. Open a new terminal afterwards to pick up the change.",
                "Remove from PATH");
    }

    public static Prompt activateProfile(String name, java.util.List<String> changes, java.util.List<String> javaWarnings, String newPath) {
        return activateProfile(name, changes, javaWarnings, java.util.List.of(), newPath);
    }

    /** {@code leftovers}: unmanaged PATH entries that still provide a tool the profile leaves out. */
    public static Prompt activateProfile(String name, java.util.List<String> changes, java.util.List<String> javaWarnings,
            java.util.List<String> leftovers, String newPath) {
        Prompt base = activateProfileCore(name, changes, javaWarnings, newPath);
        if (leftovers.isEmpty()) {
            return base;
        }
        String note = "\n\nThese entries aren't managed by the app and stay on PATH:\n  " + String.join("\n  ", leftovers)
                + "\nRemove them on that tool's Environment screen (\"Remove other entries\") if you don't want them.";
        return new Prompt(base.title(), base.message() + note, base.action());
    }

    private static Prompt activateProfileCore(String name, java.util.List<String> changes, java.util.List<String> javaWarnings, String newPath) {
        StringBuilder message = new StringBuilder();
        for (String warning : javaWarnings) {
            message.append(warning).append('\n');
        }
        if (!javaWarnings.isEmpty()) {
            message.append('\n');
        }
        message.append("Switch to ").append(name).append("?\n\n");
        for (String change : changes) {
            message.append("  ").append(change).append('\n');
        }
        message.append("\nNew user PATH:\n");
        for (String entry : newPath.split(";")) {
            message.append("  ").append(entry).append('\n');
        }
        message.append("\nOpen a new terminal afterwards to pick up the change.");
        return new Prompt("Activate " + name, message.toString(), "Activate");
    }

    public static Prompt installMissing(String name, java.util.List<String> missing, java.util.List<String> cannotInstall) {
        StringBuilder message = new StringBuilder(name).append(" uses versions that aren't installed:\n\n");
        for (String label : missing) {
            message.append("  ").append(label).append('\n');
        }
        if (!cannotInstall.isEmpty()) {
            message.append('\n').append(String.join(", ", cannotInstall))
                    .append(" can't be installed automatically. Install or adopt ")
                    .append(cannotInstall.size() == 1 ? "it" : "them").append(" first, then activate the profile.");
            return new Prompt("Missing versions", message.toString(), "Install the others");
        }
        message.append("\nInstall them, then activate ").append(name).append("? Nothing changes on PATH if an install fails.");
        return new Prompt("Missing versions", message.toString(), "Install and activate");
    }

    public static Prompt deleteProfile(String name) {
        return new Prompt("Delete profile",
                "Delete the profile " + name + "?\n\nThe environment isn't changed, and no JDK or tool is removed.",
                "Delete");
    }

    public static Prompt removeUrl(String url) {
        return new Prompt("Remove URL",
                "Remove " + url + " from the trusted URLs?\n\nCertificates already imported from it stay in each JDK's cacerts. Sync doesn't remove them.",
                "Remove");
    }

    public static Prompt syncAll() {
        return new Prompt("Sync all",
                "Update the cacerts truststore of every managed JDK?\n\nA backup of each cacerts file is made before it is changed.",
                "Sync");
    }
}
