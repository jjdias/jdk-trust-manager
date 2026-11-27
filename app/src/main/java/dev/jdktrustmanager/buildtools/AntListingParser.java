package dev.jdktrustmanager.buildtools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads versions from the Apache archive listing of {@code ant/binaries/}. */
public final class AntListingParser {

    private static final Pattern BINARY_ZIP = Pattern.compile("href=\"apache-ant-([0-9][^\"/]*)-bin\\.zip\"");

    private AntListingParser() {
    }

    public static List<String> versions(String html) {
        List<String> versions = new ArrayList<>();
        Matcher matcher = BINARY_ZIP.matcher(html);
        while (matcher.find()) {
            String version = matcher.group(1);
            if (!versions.contains(version)) {
                versions.add(version);
            }
        }
        return versions;
    }
}
