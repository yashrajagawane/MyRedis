package com.myredis.expiration;

import com.myredis.storage.StorageEngine;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically removes expired keys that have not been accessed. */
public final class ExpirationScheduler implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public ExpirationScheduler(ExpirationManager expiration, StorageEngine storage) {
        executor.scheduleAtFixedRate(() -> expiration.expirySnapshot().keySet()
                .forEach(storage::removeIfExpired), 100, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("expiration scheduler did not terminate");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
