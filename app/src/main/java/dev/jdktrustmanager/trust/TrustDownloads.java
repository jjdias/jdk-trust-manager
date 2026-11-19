package dev.jdktrustmanager.trust;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class TrustDownloads {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public Path mozilla(Path destination) throws IOException {
        byte[] body = get(URI.create(TrustSync.MOZILLA));
        String text = new String(body, StandardCharsets.UTF_8);
        if (!text.contains("-----BEGIN CERTIFICATE-----")) {
            throw new IOException("Mozilla download is not a PEM bundle");
        }
        Files.createDirectories(destination.getParent());
        Files.write(destination, body);
        return destination;
    }

    public Path testng(Path directory) throws IOException {
        return writeChain(directory, "testng.org", 443);
    }

    public List<String> certificatesFor(String url) throws IOException {
        URI uri = URI.create(url);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        if (path.endsWith(".pem") || path.endsWith(".cer") || path.endsWith(".crt") || path.endsWith(".der")) {
            return decode(get(uri));
        }
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        return chain(uri.getHost(), port);
    }

    public Path writeChain(Path directory, String host, int port) throws IOException {
        Files.createDirectories(directory);
        List<String> certificates = chain(host, port);
        for (int index = 0; index < certificates.size(); index++) {
            Files.writeString(directory.resolve((index + 1) + ".pem"), certificates.get(index));
        }
        return directory;
    }

    private List<String> chain(String host, int port) throws IOException {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port)) {
            socket.setSoTimeout(15_000);
            socket.startHandshake();
            Certificate[] chain = socket.getSession().getPeerCertificates();
            if (chain == null || chain.length == 0) {
                throw new IOException(host + " presented no certificate");
            }
            List<String> certificates = new ArrayList<>();
            for (Certificate certificate : chain) {
                if (certificate instanceof X509Certificate x509) {
                    certificates.add(pem(x509));
                }
            }
            return certificates;
        }
    }

    private static List<String> decode(byte[] body) throws IOException {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<String> certificates = new ArrayList<>();
            for (Certificate certificate : factory.generateCertificates(new java.io.ByteArrayInputStream(body))) {
                if (certificate instanceof X509Certificate x509) {
                    certificates.add(pem(x509));
                }
            }
            if (certificates.isEmpty()) {
                throw new IOException("Download did not contain a certificate");
            }
            return certificates;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Download did not contain a certificate", ex);
        }
    }

    public byte[] get(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("HTTPS only");
        }
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "JdkTrustManager")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                return readLimited(response.body());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            } catch (IOException ex) {
                last = ex;
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if (attempt == 1 || message.startsWith("HTTP ") || message.contains("larger than")) {
                    throw ex;
                }
            }
        }
        throw last;
    }

    static String pem(X509Certificate certificate) throws IOException {
        try {
            String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() + read > MAX_BYTES) {
                    throw new IOException("Certificate download is larger than 5 MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
