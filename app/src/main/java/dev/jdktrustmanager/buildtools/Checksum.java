package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.Verification;

/** A checksum file for a download and the algorithm it uses. */
public record Checksum(Verification kind, String url) {
}
