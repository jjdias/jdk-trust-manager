package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.ToolVersions;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java feature versions a tool version runs on. {@code max} is null when there's no known upper limit. */
public record JavaRange(int min, Integer max) {

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^\\d+(\\.\\d+)*");

    public boolean allows(int javaMajor) {
        return javaMajor >= min && (max == null || javaMajor <= max);
    }

    public String describe() {
        return max == null ? "Java " + min + " or newer" : "Java " + min + " to " + max;
    }

    /** One row of a range table: tool versions from {@code from} (inclusive) get {@code value}. */
    record Step(String from, Integer value) {
    }

    /** The value of the last row whose {@code from} is at or below {@code version}'s numeric part. */
    static Integer lookup(List<Step> steps, String version, Integer fallback) {
        Matcher matcher = NUMERIC_PREFIX.matcher(version);
        String numeric = matcher.find() ? matcher.group() : "0";
        Integer value = fallback;
        for (Step step : steps) {
            if (ToolVersions.compare(numeric, step.from()) >= 0) {
                value = step.value();
            }
        }
        return value;
    }
}
