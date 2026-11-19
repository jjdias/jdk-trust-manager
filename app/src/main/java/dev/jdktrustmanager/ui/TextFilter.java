package dev.jdktrustmanager.ui;

import java.util.Locale;

public final class TextFilter {

    private TextFilter() {
    }

    public static boolean matches(String value, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
}
