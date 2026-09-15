package com.myredis.observability;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerMetricsTest {
    @Test
    void reportsBucketedLatencyPercentiles() {
        ServerMetrics metrics = new ServerMetrics();
        for (int index = 0; index < 98; index++) {
            metrics.recordCommand("GET");
            metrics.recordCommandLatency(2_000);
        }
        metrics.recordCommand("GET");
        metrics.recordCommandLatency(20_000);
        metrics.recordCommand("GET");
        metrics.recordCommandLatency(20_000);

        String info = metrics.info();

        assertTrue(info.contains("command_latency_p95_us:5"));
        assertTrue(info.contains("command_latency_p99_us:25"));
    }
}
