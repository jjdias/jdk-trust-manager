package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateEntry;
import dev.jdktrustmanager.model.Installation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** A standalone HTML page summarising managed JDKs and certificates that expire soon. Every value is escaped. */
public final class HtmlReport {

    static final int EXPIRY_WINDOW_DAYS = 30;

    public String render(List<Installation> installations, String javaHome, List<CertificateEntry> certificates) {
        return render(installations, javaHome, null, certificates, Instant.now());
    }

    public String render(List<Installation> installations, String javaHome, String cacerts,
            List<CertificateEntry> certificates, Instant now) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>JDK Trust Manager report</title><style>")
                .append("body{font-family:Segoe UI,system-ui,sans-serif;margin:32px;color:#222}")
                .append("table{border-collapse:collapse;margin:8px 0 24px}")
                .append("th,td{border:1px solid #ccc;padding:6px 10px;text-align:left;vertical-align:top}")
                .append("th{background:#f3f3f3}.muted{color:#666}.expired{color:#b00020;font-weight:600}")
                .append("</style></head><body>\n");
        html.append("<h1>JDK Trust Manager report</h1>\n");
        html.append("<p class=\"muted\">Generated ").append(escape(now.truncatedTo(ChronoUnit.SECONDS).toString())).append("</p>\n");
        html.append("<p>JAVA_HOME: <code>").append(escape(blankToNone(javaHome))).append("</code></p>\n");

        html.append("<h2>Managed JDKs</h2>\n");
        if (installations.isEmpty()) {
            html.append("<p class=\"muted\">None.</p>\n");
        } else {
            html.append("<table><tr><th>Vendor</th><th>Major</th><th>Version</th><th>Path</th><th>Source</th><th>Notes</th></tr>\n");
            for (Installation installation : installations) {
                html.append("<tr><td>").append(escape(installation.vendorSlug()))
                        .append("</td><td>").append(installation.major())
                        .append("</td><td>").append(escape(installation.javaVersion()))
                        .append("</td><td><code>").append(escape(installation.path())).append("</code>")
                        .append("</td><td>").append(escape(installation.source() == null ? "" : installation.source().name().toLowerCase()))
                        .append("</td><td>").append(flags(installation))
                        .append("</td></tr>\n");
            }
            html.append("</table>\n");
        }

        Instant soon = now.plus(EXPIRY_WINDOW_DAYS, ChronoUnit.DAYS);
        List<CertificateEntry> expiring = certificates.stream()
                .filter(certificate -> certificate.notAfter() != null && !certificate.notAfter().isAfter(soon))
                .sorted(Comparator.comparing(CertificateEntry::notAfter))
                .toList();
        html.append("<h2>Certificates expiring within ").append(EXPIRY_WINDOW_DAYS).append(" days</h2>\n");
        if (cacerts != null && !cacerts.isBlank()) {
            html.append("<p class=\"muted\">From <code>").append(escape(cacerts)).append("</code>, ")
                    .append(certificates.size()).append(" certificates checked.</p>\n");
        }
        if (expiring.isEmpty()) {
            html.append("<p class=\"muted\">None.</p>\n");
        } else {
            html.append("<table><tr><th>Alias</th><th>Subject</th><th>Expires</th></tr>\n");
            for (CertificateEntry certificate : expiring) {
                boolean expired = certificate.notAfter().isBefore(now);
                html.append("<tr><td>").append(escape(certificate.alias()))
                        .append("</td><td>").append(escape(certificate.subject()))
                        .append("</td><td").append(expired ? " class=\"expired\"" : "").append('>')
                        .append(escape(certificate.notAfter().toString())).append(expired ? " (expired)" : "")
                        .append("</td></tr>\n");
            }
            html.append("</table>\n");
        }
        html.append("</body></html>\n");
        return html.toString();
    }

    private static String flags(Installation installation) {
        StringBuilder text = new StringBuilder();
        if (installation.active()) {
            text.append("JAVA_HOME");
        }
        if (installation.pinned()) {
            text.append(text.isEmpty() ? "" : ", ").append("pinned");
        }
        return text.toString();
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "(not set)" : value;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            switch (character) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(character);
            }
        }
        return out.toString();
    }
}
