package com.myredis.benchmark;

import com.myredis.storage.InMemoryStorageEngine;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class StorageBenchmark {
    private final InMemoryStorageEngine storage = new InMemoryStorageEngine();

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        storage.setString("key", "value");
    }

    @Benchmark
    public void set() {
        storage.setString("key", "value");
    }

    @Benchmark
    public String get() {
        return storage.getString("key").orElseThrow();
    }
}
