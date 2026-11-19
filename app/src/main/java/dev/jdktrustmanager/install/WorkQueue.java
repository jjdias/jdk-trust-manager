package dev.jdktrustmanager.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class WorkQueue implements AutoCloseable {

    public enum Kind {
        INSTALL, UNINSTALL, ADOPT, TRUST_SYNC
    }

    public enum State {
        QUEUED, DOWNLOADING, VERIFYING, UNPACKING, UPDATING_TRUSTSTORE, FAILED, DONE
    }

    public record Event(Kind kind, State state, String message, double fraction) {
        public Event(Kind kind, State state, String message) {
            this(kind, state, message, Double.NaN);
        }
    }

    public interface Job {
        Kind kind();

        void run(Progress progress) throws Exception;
    }

    public interface Progress {
        void state(State state);

        void log(String line);

        void bytes(long read, long total);

        boolean cancelled();
    }

    private final Path logFile;
    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jkm-work");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean cancelDownload = new AtomicBoolean();
    private volatile boolean unzipStarted;
    private long lastBytePublish;

    public WorkQueue(Path logFile) {
        this.logFile = logFile;
    }

    public void subscribe(Consumer<Event> listener) {
        listeners.add(listener);
    }

    public Future<?> submit(Job job) {
        publish(job.kind(), State.QUEUED, "queued");
        return executor.submit(() -> execute(job));
    }

    public void cancelDownload() {
        cancelDownload.set(true);
    }

    public boolean unzipStarted() {
        return unzipStarted;
    }

    public void markUnzipStarted() {
        unzipStarted = true;
    }

    private void execute(Job job) {
        cancelDownload.set(false);
        unzipStarted = false;
        Progress progress = new Progress() {
            @Override
            public void state(State state) {
                publish(job.kind(), state, state.name().toLowerCase());
            }

            @Override
            public void log(String line) {
                if (line != null && (line.contains("storepass") || line.contains("PRIVATE KEY"))) {
                    line = "[redacted]";
                }
                publish(job.kind(), null, line, Double.NaN);
                append(line);
            }

            @Override
            public void bytes(long read, long total) {
                long now = System.nanoTime();
                if (read != total && now - lastBytePublish < 100_000_000L) {
                    return;
                }
                lastBytePublish = now;
                double fraction = total <= 0 ? Double.NaN : Math.min(1d, (double) read / total);
                publish(job.kind(), State.DOWNLOADING, formatBytes(read) + " / " + formatBytes(total), fraction);
            }

            @Override
            public boolean cancelled() {
                return cancelDownload.get() && !unzipStarted;
            }
        };
        try {
            job.run(progress);
            publish(job.kind(), State.DONE, "done");
        } catch (Exception ex) {
            publish(job.kind(), State.FAILED, ex.getMessage() == null ? "failed" : ex.getMessage());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024d / 1024d);
    }

    private void publish(Kind kind, State state, String message) {
        publish(kind, state, message, Double.NaN);
    }

    private void publish(Kind kind, State state, String message, double fraction) {
        Event event = new Event(kind, state, message, fraction);
        for (Consumer<Event> listener : listeners) {
            listener.accept(event);
        }
    }

    private void append(String line) {
        try {
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            Files.writeString(logFile, Instant.now() + " " + line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // The UI still receives the line.
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
