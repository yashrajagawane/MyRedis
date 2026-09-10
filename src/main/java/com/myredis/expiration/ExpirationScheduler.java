package com.myredis.expiration;

import com.myredis.storage.StorageEngine;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Periodically removes expired keys that have not been accessed. */
public final class ExpirationScheduler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpirationScheduler.class);
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
                LOGGER.warn("Expiration scheduler did not terminate before shutdown timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
