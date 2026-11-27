package dev.jdktrustmanager.buildtools;

import java.io.IOException;

/** The server answered 404: the file doesn't exist, as opposed to the network failing. */
public final class NotFoundException extends IOException {

    public NotFoundException(String url) {
        super("Not found: " + url);
    }
}
