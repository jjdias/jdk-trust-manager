package dev.jdktrustmanager.env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class UserEnvironment {

    public void apply(String javaHome, String path) throws IOException, InterruptedException {
        reg("JAVA_HOME", "REG_SZ", javaHome);
        reg("Path", "REG_EXPAND_SZ", path);
        broadcast();
    }

    private static void reg(String name, String type, String value) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "add", "HKCU\\Environment", "/v", name, "/t", type, "/d", value, "/f")
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) {
            throw new IOException("Could not write " + name);
        }
    }

    private static void broadcast() throws IOException, InterruptedException {
        String script = "$sig = '[DllImport(\"user32.dll\", CharSet=CharSet.Auto)] public static extern IntPtr SendMessageTimeout(IntPtr h, int m, IntPtr w, string l, int f, int t, out IntPtr r);';"
                + " $t = Add-Type -MemberDefinition $sig -Name JkmEnv -Namespace Jkm -PassThru;"
                + " $r = [IntPtr]::Zero; [void]$t::SendMessageTimeout([IntPtr]0xffff, 0x1A, [IntPtr]::Zero, 'Environment', 2, 5000, [ref]$r)";
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }

    public static String whereJava() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("where.exe", "java")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output.trim();
    }
}
