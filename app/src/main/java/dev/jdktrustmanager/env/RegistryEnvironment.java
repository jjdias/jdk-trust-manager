package dev.jdktrustmanager.env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegistryEnvironment {

    public static final String USER = "HKCU\\Environment";
    public static final String MACHINE = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";

    private static final Pattern DATA = Pattern.compile("REG_(?:EXPAND_)?SZ\\s+(.*)$");
    private static final Pattern ENTRY = Pattern.compile("^(.+?)\\s+REG_(?:EXPAND_)?SZ\\s+(.*)$");

    private RegistryEnvironment() {
    }

    public static String query(String key, String name) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("reg", "query", key, "/v", name)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            return "";
        }
        return value(output);
    }

    public static Map<String, String> queryAll(String key) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("reg", "query", key)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            return Map.of();
        }
        return entries(output);
    }

    public static Map<String, String> entries(String output) {
        Map<String, String> values = new LinkedHashMap<>();
        if (output == null) {
            return values;
        }
        for (String line : output.split("\\R")) {
            Matcher matcher = ENTRY.matcher(line.trim());
            if (matcher.find()) {
                values.put(matcher.group(1).trim(), matcher.group(2).trim());
            }
        }
        return values;
    }

    public static String value(String output) {
        if (output == null) {
            return "";
        }
        for (String line : output.split("\\R")) {
            Matcher matcher = DATA.matcher(line.trim());
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }
}
