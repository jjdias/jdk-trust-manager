package dev.jdktrustmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A JDK by what it is (vendor slug and major), not where it is. The folder is worked out from the install root. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JdkRef(String vendorSlug, int major) {
}
