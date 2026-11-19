package dev.jdktrustmanager.catalog;

import java.io.IOException;

public interface DiscoTransport {

    String get(String pathAndQuery) throws IOException;
}
