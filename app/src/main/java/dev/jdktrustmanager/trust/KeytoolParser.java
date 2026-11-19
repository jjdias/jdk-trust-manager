package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateEntry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeytoolParser {

    private static final DateTimeFormatter KEYTOOL_DATE =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);

    public List<CertificateEntry> parse(String text) {
        List<CertificateEntry> entries = new ArrayList<>();
        String[] blocks = text.split("(?=Alias name:)");
        for (String block : blocks) {
            if (!block.contains("Alias name:")) {
                continue;
            }
            String alias = value(block, "Alias name:");
            String subject = value(block, "Owner:");
            String issuer = value(block, "Issuer:");
            String serial = value(block, "Serial number:");
            String algorithm = value(block, "Signature algorithm name:");
            String sha = value(block, "SHA256:").replace(":", "");
            String type = block.contains("PrivateKeyEntry") ? "private key present" : value(block, "Entry type:");
            boolean ca = block.contains("CA:true");
            Instant[] validity = validity(block);
            entries.add(new CertificateEntry(alias, subject, issuer, serial, algorithm, sha, validity[0], validity[1], type, ca));
        }
        return entries;
    }

    private static Instant[] validity(String block) {
        int from = block.indexOf("Valid from:");
        int until = block.indexOf(" until:");
        if (from < 0 || until < 0) {
            return new Instant[] {null, null};
        }
        String start = block.substring(from + "Valid from:".length(), until).trim();
        int lineEnd = block.indexOf('\n', until);
        String end = block.substring(until + " until:".length(), lineEnd < 0 ? block.length() : lineEnd).trim();
        return new Instant[] {parseDate(start), parseDate(end)};
    }

    private static Instant parseDate(String text) {
        try {
            return ZonedDateTime.parse(text, KEYTOOL_DATE.withZone(ZoneId.of("UTC"))).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String value(String block, String label) {
        int start = block.indexOf(label);
        if (start < 0) {
            return "";
        }
        int lineEnd = block.indexOf('\n', start);
        String line = block.substring(start + label.length(), lineEnd < 0 ? block.length() : lineEnd);
        return line.trim();
    }
}
