package dev.jdktrustmanager.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Orders build tool versions such as {@code 3.0-alpha-2}, {@code 4.0.0-rc-7}, {@code 2.1.0-M1}, and {@code 1.10.15}.
 * Numbers compare numerically, and a release sorts after its own pre-releases.
 */
public final class ToolVersions {

    public static final Comparator<String> NEWEST_FIRST = (left, right) -> compare(right, left);

    private static final Pattern MILESTONE = Pattern.compile(".*-m\\d+.*");

    private ToolVersions() {
    }

    public static int compare(String left, String right) {
        List<String> a = tokens(left);
        List<String> b = tokens(right);
        for (int index = 0; index < Math.max(a.size(), b.size()); index++) {
            String x = index < a.size() ? a.get(index) : null;
            String y = index < b.size() ? b.get(index) : null;
            int result = compareToken(x, y);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    public static boolean isPreRelease(String version) {
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.contains("alpha") || lower.contains("beta") || lower.contains("rc") || lower.contains("milestone")
                || MILESTONE.matcher(lower).matches();
    }

    private static int compareToken(String x, String y) {
        boolean xNumber = x != null && isNumber(x);
        boolean yNumber = y != null && isNumber(y);
        if (xNumber && yNumber) {
            return new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
        }
        // A missing part counts as 0 against a number ("3.0" < "3.0.1") and as a release against a qualifier.
        if (x == null && yNumber) {
            return new java.math.BigInteger(y).signum() == 0 ? 0 : -1;
        }
        if (y == null && xNumber) {
            return new java.math.BigInteger(x).signum() == 0 ? 0 : 1;
        }
        return Integer.compare(rank(x), rank(y));
    }

    /** Numbers outrank every qualifier; a missing part (a release) outranks pre-release qualifiers. */
    private static int rank(String token) {
        if (token == null) {
            return 5;
        }
        if (isNumber(token)) {
            return 6;
        }
        return switch (token) {
            case "alpha", "a" -> 1;
            case "beta", "b" -> 2;
            case "milestone", "m" -> 3;
            case "rc", "cr" -> 4;
            default -> 0;
        };
    }

    private static List<String> tokens(String version) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Boolean digits = null;
        for (char character : version.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == '.' || character == '-' || character == '_' || character == '+') {
                flush(tokens, current);
                digits = null;
                continue;
            }
            boolean isDigit = Character.isDigit(character);
            if (digits != null && digits != isDigit) {
                flush(tokens, current);
            }
            digits = isDigit;
            current.append(character);
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isNumber(String token) {
        return !token.isEmpty() && token.chars().allMatch(Character::isDigit);
    }
}
