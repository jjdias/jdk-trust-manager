package dev.jdktrustmanager.install;

import java.util.ArrayList;
import java.util.List;

public final class JavaSoftHomes {

    private JavaSoftHomes() {
    }

    public static List<String> parse(List<String> lines) {
        List<String> homes = new ArrayList<>();
        if (lines == null) {
            return homes;
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("JavaHome")) {
                continue;
            }
            int type = trimmed.indexOf("REG_");
            if (type < 0) {
                continue;
            }
            int value = trimmed.indexOf(' ', type);
            if (value > 0) {
                homes.add(trimmed.substring(value).trim());
            }
        }
        return homes;
    }
}
