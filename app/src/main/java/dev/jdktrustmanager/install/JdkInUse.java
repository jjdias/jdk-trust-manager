package dev.jdktrustmanager.install;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Programs running from a JDK folder. Windows won't rename or delete a folder while a program has files open in it, so
 * an update checks this before it downloads anything.
 */
public final class JdkInUse {

    /** A running process and the full path of its executable. */
    public record Holder(long pid, String command) {
    }

    /** Thrown before any download or change when the JDK folder can't be replaced right now. */
    public static final class InUseException extends IOException {
        public InUseException(String message) {
            super(message);
        }
    }

    private JdkInUse() {
    }

    /** Running processes whose executable sits inside {@code home}. */
    public static List<Holder> holders(Path home) {
        List<Holder> all = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(process -> process.info().command()
                .ifPresent(command -> all.add(new Holder(process.pid(), command))));
        return filter(home, all);
    }

    static List<Holder> filter(Path home, List<Holder> all) {
        String prefix = home.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        String folder = prefix.endsWith("\\") ? prefix : prefix + "\\";
        return all.stream()
                .filter(holder -> holder.command() != null && holder.command().toLowerCase(Locale.ROOT).replace('/', '\\').startsWith(folder))
                .toList();
    }

    static String message(Path home, List<Holder> holders) {
        long self = ProcessHandle.current().pid();
        List<String> names = new ArrayList<>();
        for (Holder holder : holders) {
            String exe = Path.of(holder.command()).getFileName().toString();
            names.add(holder.pid() == self ? "JDK Trust Manager itself (it's running on this JDK)" : exe + " (pid " + holder.pid() + ")");
        }
        return home + " is in use by " + String.join(", ", names) + ". Close "
                + (holders.size() == 1 ? "it" : "them") + " and try again. Nothing was downloaded or changed.";
    }
}
