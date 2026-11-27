package dev.jdktrustmanager.buildtools;

import java.io.IOException;

/** A download failed its signature or checksum check. The zip has already been deleted. */
public final class VerificationException extends IOException {

    public VerificationException(String message) {
        super(message);
    }
}
