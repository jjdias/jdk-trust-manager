package dev.jdktrustmanager.buildtools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs a freshly installed tool's version command with a chosen JDK, as a last check before it's kept. */
public final class RunCheck {

    private RunCheck() {
    }

    public static String run(BuildTool tool, Path home, Path javaHome) throws IOException {
        Path launcher = ToolLayout.launcher(tool, home);
        if (launcher == null) {
            throw new IOException("No launcher in " + home);
        }
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", launcher.toString(), tool.versionFlag())
                .directory(home.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.remove("JDK_JAVA_OPTIONS");
        environment.remove("_JAVA_OPTIONS");
        environment.put("JAVA_HOME", javaHome.toString());
        for (String variable : tool.homeVariables()) {
            environment.put(variable, home.toString());
        }
        String path = environment.getOrDefault("Path", environment.getOrDefault("PATH", ""));
        environment.put("Path", javaHome.resolve("bin") + ";" + home.resolve("bin") + ";" + path);
        Process process = builder.start();
        process.getOutputStream().close();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return "";
            }
        });
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(tool.displayName() + " didn't answer within 120 seconds");
            }
            String text = output.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IOException(tool.displayName() + " exited with " + process.exitValue() + ": " + text.strip());
            }
            return text;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(ex);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IOException("Couldn't read " + tool.displayName() + " output", ex);
        }
    }
}
