package dev.jdktrustmanager.catalog;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpDiscoTransport implements DiscoTransport {

    public static final String BASE = "https://api.foojay.io/disco/v3.0";
    public static final String USER_AGENT = "JdkTrustManager";

    private final HttpClient client;

    public HttpDiscoTransport() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Override
    public String get(String pathAndQuery) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + pathAndQuery))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            return body(client.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException first) {
            if (!connectionDropped(first)) {
                throw first;
            }
            try {
                return body(client.send(request, HttpResponse.BodyHandlers.ofString()));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    private static String body(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Disco HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static boolean connectionDropped(IOException exception) {
        return exception instanceof ConnectException
                || exception instanceof HttpConnectTimeoutException
                || exception instanceof NoRouteToHostException
                || exception instanceof java.net.SocketException;
    }
}
