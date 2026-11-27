package dev.jdktrustmanager.buildtools;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** {@link ToolHttp} over the system proxy, following redirects (services.gradle.org redirects to downloads.gradle.org). */
public final class HttpToolHttp implements ToolHttp {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(ProxySelector.getDefault())
            .build();

    @Override
    public byte[] get(String url) throws IOException {
        ToolHttp.requireHttps(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "JdkTrustManager")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            return body(url, send(request));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpConnectTimeoutException | SocketException first) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static byte[] body(String url, HttpResponse<byte[]> response) throws IOException {
        if (response.statusCode() == 404) {
            throw new NotFoundException(url);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        if (!"https".equalsIgnoreCase(response.uri().getScheme())) {
            throw new IOException("Redirected away from HTTPS: " + response.uri());
        }
        return response.body();
    }
}
