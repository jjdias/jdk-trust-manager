package dev.jdktrustmanager.env;

import dev.jdktrustmanager.model.Installations;
import java.util.ArrayList;
import java.util.List;

public final class JdkChoices {

    private JdkChoices() {
    }

    public static List<String> candidates(String javaHome, List<String> homes) {
        List<String> choices = new ArrayList<>();
        if (javaHome != null && !javaHome.isBlank()) {
            choices.add(javaHome.trim());
        }
        for (String home : homes) {
            if (home != null && !home.isBlank() && choices.stream().noneMatch(choice -> Installations.samePath(choice, home))) {
                choices.add(home.trim());
            }
        }
        return choices;
    }
}
