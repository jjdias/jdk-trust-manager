package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateEntry;
import dev.jdktrustmanager.model.Installation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class HtmlReport {

    public String render(List<Installation> installations, String javaHome, List<CertificateEntry> certificates) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><h1>JDK Trust Manager</h1>");
        html.append("<p>JAVA_HOME ").append(javaHome).append("</p><ul>");
        for (Installation installation : installations) {
            html.append("<li>").append(installation.path()).append(" major ").append(installation.major()).append("</li>");
        }
        html.append("</ul><h2>Expiring</h2><ul>");
        Instant soon = Instant.now().plus(30, ChronoUnit.DAYS);
        for (CertificateEntry certificate : certificates) {
            if (certificate.notAfter() != null && !certificate.notAfter().isAfter(soon)) {
                html.append("<li>").append(certificate.alias()).append(" ").append(certificate.notAfter()).append("</li>");
            }
        }
        html.append("</ul></body></html>");
        return html.toString();
    }
}
