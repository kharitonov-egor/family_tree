package com.egakh.familytree.plugin.storage;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes encoded history on one background thread, in the order the saves were made. */
public final class SaveQueue {
    private final StoreFiles files;
    private final Logger logger;
    private final ExecutorService writer;

    public SaveQueue(StoreFiles files, Logger logger) {
        this(files, logger, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Family Tree save");
            thread.setDaemon(true);
            return thread;
        }));
    }

    SaveQueue(StoreFiles files, Logger logger, ExecutorService writer) {
        this.files = files;
        this.logger = logger;
        this.writer = writer;
    }

    /** Runs {@code onFailure} on the writer thread when the write fails. After {@link #finish}, writes run on the caller. */
    public void submit(byte[] bytes, Runnable onFailure) {
        Runnable write = () -> {
            try {
                files.write(bytes);
            } catch (IOException | RuntimeException failure) {
                logger.log(Level.SEVERE, "Could not save Family Tree history to " + files.file(), failure);
                onFailure.run();
            }
        };
        if (writer.isShutdown()) write.run();
        else writer.execute(write);
    }

    /** Takes no more writes and waits for the queued ones, so the last save submitted is the one left on disk. */
    public boolean finish(long timeout, TimeUnit unit) throws InterruptedException {
        writer.shutdown();
        return writer.awaitTermination(timeout, unit);
    }
}
