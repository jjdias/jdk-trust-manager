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
