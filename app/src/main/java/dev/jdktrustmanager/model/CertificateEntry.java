package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CertificateEntry(
        String alias,
        String subject,
        String issuer,
        String serial,
        String signatureAlgorithm,
        String sha256,
        Instant notBefore,
        Instant notAfter,
        String entryType,
        boolean ca) {
}
