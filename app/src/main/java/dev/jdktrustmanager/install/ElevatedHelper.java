package dev.jdktrustmanager.install;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ElevatedHelper {

    static final String USERS_SID = "*S-1-5-32-545:(OI)(CI)M";

    private ElevatedHelper() {
    }

    public static void main(String[] args) throws Exception {
        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println(handle(input));
    }

    public static String handle(String json) throws IOException, InterruptedException {
        JsonNode node = JsonMapper.builder().build().readTree(json);
        String command = node.path("command").asText();
        if ("create-root".equals(command)) {
            return createRoot(Path.of(node.path("path").asText()));
        }
        if ("set-system-env".equals(command)) {
            return setSystemEnv(node.path("javaHome").asText(), node.path("path").asText());
        }
        return "{\"ok\":false,\"error\":\"unknown command\"}";
    }

    static String createRoot(Path path) throws IOException, InterruptedException {
        Files.createDirectories(path);
        Process process = new ProcessBuilder("icacls", path.toString(), "/grant", USERS_SID)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            return "{\"ok\":false,\"error\":" + quote(output.trim()) + "}";
        }
        return "{\"ok\":true,\"acl\":\"S-1-5-32-545:(OI)(CI)M\"}";
    }

    static String setSystemEnv(String javaHome, String path) throws IOException, InterruptedException {
        int home = reg("JAVA_HOME", "REG_SZ", javaHome);
        int envPath = reg("Path", "REG_EXPAND_SZ", path);
        boolean ok = home == 0 && envPath == 0;
        return "{\"ok\":" + ok + "}";
    }

    private static int reg(String name, String type, String value) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "add",
                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment",
                "/v", name, "/t", type, "/d", value, "/f")
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
