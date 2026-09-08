package com.myredis.config;

import com.myredis.persistence.FsyncPolicy;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Immutable runtime configuration for a MyRedis server. */
public record ServerConfig(
        String host,
        int port,
        boolean aofEnabled,
        Path aofPath,
        Path snapshotPath,
        FsyncPolicy fsyncPolicy,
        long snapshotIntervalSeconds,
        String logLevel,
        int maxValueBytes,
        int maxArrayElements,
        int maxConnections) {

    public ServerConfig {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host must not be blank");
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("port must be between 0 and 65535");
        if (aofPath == null || snapshotPath == null) throw new IllegalArgumentException("persistence paths are required");
        if (fsyncPolicy == null) throw new IllegalArgumentException("fsync policy is required");
        if (snapshotIntervalSeconds < 0) throw new IllegalArgumentException("snapshot interval must not be negative");
        if (logLevel == null || logLevel.isBlank()) throw new IllegalArgumentException("log level must not be blank");
        if (!Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(logLevel.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("log level must be TRACE, DEBUG, INFO, WARN, or ERROR");
        }
        if (maxValueBytes < 1) throw new IllegalArgumentException("max value bytes must be positive");
        if (maxArrayElements < 1) throw new IllegalArgumentException("max array elements must be positive");
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
    }
}
