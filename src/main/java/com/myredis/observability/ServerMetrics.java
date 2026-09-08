package com.myredis.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Lightweight process-local counters for operational diagnostics. */
public final class ServerMetrics {
    private final AtomicLong commandsProcessed = new AtomicLong();
    private final AtomicLong connectedClients = new AtomicLong();
    private final AtomicLong keyspaceHits = new AtomicLong();
    private final AtomicLong keyspaceMisses = new AtomicLong();
    private final LongAdder commandLatencyNanos = new LongAdder();
    private final AtomicLong maxCommandLatencyNanos = new AtomicLong();
    private final Map<String, LongAdder> commandsByName = new ConcurrentHashMap<>();

    public void recordCommand(String command) {
        commandsProcessed.incrementAndGet();
        commandsByName.computeIfAbsent(command, ignored -> new LongAdder()).increment();
    }

    public void recordCommandLatency(long latencyNanos) {
        long sanitizedLatency = Math.max(0, latencyNanos);
        commandLatencyNanos.add(sanitizedLatency);
        maxCommandLatencyNanos.accumulateAndGet(sanitizedLatency, Math::max);
    }

    public void clientConnected() {
        connectedClients.incrementAndGet();
    }

    public void clientDisconnected() {
        connectedClients.updateAndGet(current -> Math.max(0, current - 1));
    }

    public void recordGetHit() {
        keyspaceHits.incrementAndGet();
    }

    public void recordGetMiss() {
        keyspaceMisses.incrementAndGet();
    }

    public String info() {
        return info(0, 0, 0, 0);
    }

    public String info(long expiredKeys) {
        return info(expiredKeys, 0, 0, 0);
    }

    public String info(long expiredKeys, long aofWrites, long snapshots) {
        return info(expiredKeys, aofWrites, snapshots, 0);
    }

    public String info(long expiredKeys, long aofWrites, long snapshots, long persistenceErrors) {
        String commandCounts = commandsByName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "command_" + entry.getKey().toLowerCase() + ":" + entry.getValue().sum())
                .collect(Collectors.joining("\r\n"));
        long processed = commandsProcessed.get();
        long averageLatencyMicros = processed == 0
                ? 0 : commandLatencyNanos.sum() / processed / 1_000;
        long maxLatencyMicros = maxCommandLatencyNanos.get() / 1_000;
        return "# Server\r\nmyredis_runtime:java21\r\n# Clients\r\nconnected_clients:"
                + connectedClients.get() + "\r\n# Stats\r\ncommands_processed:"
                + processed + "\r\ncommand_latency_avg_us:" + averageLatencyMicros
                + "\r\ncommand_latency_max_us:" + maxLatencyMicros + "\r\nexpired_keys:" + expiredKeys
                + "\r\naof_writes:" + aofWrites + "\r\nsnapshots:" + snapshots
                + "\r\npersistence_errors:" + persistenceErrors
                + "\r\nkeyspace_hits:" + keyspaceHits.get()
                + "\r\nkeyspace_misses:" + keyspaceMisses.get() + "\r\n" + commandCounts + "\r\n";
    }
}
