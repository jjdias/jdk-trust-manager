package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SourcesDocument(List<UrlEntry> urls, List<CertificateChoice> certificates) {
}
