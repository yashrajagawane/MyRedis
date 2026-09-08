package com.myredis.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Lightweight process-local counters for operational diagnostics. */
public final class ServerMetrics {
    private final AtomicLong commandsProcessed = new AtomicLong();
    private final Map<String, LongAdder> commandsByName = new ConcurrentHashMap<>();

    public void recordCommand(String command) {
        commandsProcessed.incrementAndGet();
        commandsByName.computeIfAbsent(command, ignored -> new LongAdder()).increment();
    }

    public String info() {
        String commandCounts = commandsByName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "command_" + entry.getKey().toLowerCase() + ":" + entry.getValue().sum())
                .collect(Collectors.joining("\r\n"));
        return "# Server\r\nmyredis_runtime:java21\r\n# Stats\r\ncommands_processed:"
                + commandsProcessed.get() + "\r\n" + commandCounts + "\r\n";
    }
}
