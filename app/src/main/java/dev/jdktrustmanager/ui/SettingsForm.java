package dev.jdktrustmanager.ui;

public final class SettingsForm {

    private SettingsForm() {
    }

    public static long parseHours(String text) {
        try {
            long hours = Long.parseLong(text == null ? "" : text.trim());
            if (hours >= 1) {
                return hours;
            }
        } catch (NumberFormatException ignored) {
            // Reported below with a message the user can act on.
        }
        throw new IllegalArgumentException("Catalog cache hours must be a whole number of 1 or more.");
    }
}
