package dev.jdktrustmanager.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkQueueTest {

    @TempDir
    Path directory;

    @Test
    void installsRunOneAfterAnotherAndCancelSkipsRegistration() throws Exception {
        WorkQueue queue = new WorkQueue(directory.resolve("log.txt"));
        List<String> order = new ArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.INSTALL;
            }

            @Override
            public void run(WorkQueue.Progress progress) throws Exception {
                order.add("first");
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
                progress.state(WorkQueue.State.UNPACKING);
            }
        });
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.INSTALL;
            }

            @Override
            public void run(WorkQueue.Progress progress) {
                order.add("second");
            }
        });
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("first"), List.copyOf(order));
        releaseFirst.countDown();
        Thread.sleep(300);
        assertEquals(List.of("first", "second"), order);

        List<String> registered = new ArrayList<>();
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.INSTALL;
            }

            @Override
            public void run(WorkQueue.Progress progress) {
                progress.state(WorkQueue.State.DOWNLOADING);
                queue.cancelDownload();
                if (progress.cancelled()) {
                    return;
                }
                queue.markUnzipStarted();
                registered.add("installed");
            }
        });
        Thread.sleep(300);
        assertTrue(registered.isEmpty());
        progressLogDoesNotKeepSecrets(queue);
        queue.close();
        assertFalse(Files.readString(directory.resolve("log.txt")).contains("changeit"));
    }

    private void progressLogDoesNotKeepSecrets(WorkQueue queue) throws Exception {
        queue.submit(new WorkQueue.Job() {
            @Override
            public WorkQueue.Kind kind() {
                return WorkQueue.Kind.TRUST_SYNC;
            }

            @Override
            public void run(WorkQueue.Progress progress) {
                progress.log("storepass changeit");
            }
        });
        Thread.sleep(200);
    }
}
