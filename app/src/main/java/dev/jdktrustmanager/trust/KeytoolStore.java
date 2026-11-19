package dev.jdktrustmanager.trust;

import dev.jdktrustmanager.model.CertificateEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public final class KeytoolStore {

    private final KeytoolParser parser = new KeytoolParser();

    public List<CertificateEntry> listCacerts(Path javaHome, int major) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add(javaHome.resolve("bin").resolve("keytool.exe").toString());
        command.add("-J-Duser.language=en");
        command.add("-J-Duser.country=US");
        command.add("-list");
        command.add("-v");
        if (major <= 8) {
            command.add("-keystore");
            command.add(javaHome.resolve("lib").resolve("security").resolve("cacerts").toString());
        } else {
            command.add("-cacerts");
        }
        command.add("-storepass");
        command.add("changeit");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return parser.parse(output);
    }
}
