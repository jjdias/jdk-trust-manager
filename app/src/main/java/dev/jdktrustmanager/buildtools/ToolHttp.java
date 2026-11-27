package dev.jdktrustmanager.buildtools;

import java.io.IOException;

/** Small HTTPS GETs: version lists, checksum files, signatures, and KEYS. A 404 throws {@link NotFoundException}. */
@FunctionalInterface
public interface ToolHttp {

    byte[] get(String url) throws IOException;

    static void requireHttps(String url) throws IOException {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IOException("HTTPS required: " + url);
        }
    }
}
