package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TrustCompare {

    public record Diff(List<String> onlyLeft, List<String> onlyRight, List<String> differentFingerprint) {
    }

    public Diff compare(List<CertificateEntry> left, List<CertificateEntry> right) {
        Set<String> leftPrints = prints(left);
        Set<String> rightPrints = prints(right);
        List<String> onlyLeft = new ArrayList<>();
        List<String> onlyRight = new ArrayList<>();
        for (String print : leftPrints) {
            if (!rightPrints.contains(print)) {
                onlyLeft.add(print);
            }
        }
        for (String print : rightPrints) {
            if (!leftPrints.contains(print)) {
                onlyRight.add(print);
            }
        }
        return new Diff(onlyLeft, onlyRight, List.of());
    }

    private static Set<String> prints(List<CertificateEntry> entries) {
        Set<String> prints = new LinkedHashSet<>();
        for (CertificateEntry entry : entries) {
            if (entry.sha256() != null && !entry.sha256().isBlank()) {
                prints.add(entry.sha256());
            }
        }
        return prints;
    }
}
