package com.myredis.benchmark;

import com.myredis.storage.InMemoryStorageEngine;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/** Measures atomic counter throughput with multiple clients sharing one key. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ConcurrentStorageBenchmark {
    private InMemoryStorageEngine storage;

    @Setup(Level.Iteration)
    public void setup() {
        storage = new InMemoryStorageEngine();
        storage.setString("counter", "0");
    }

    @Benchmark
    @Threads(1)
    public long incrementOneClient() {
        return storage.incrementString("counter", 1);
    }

    @Benchmark
    @Threads(10)
    public long incrementTenClients() {
        return storage.incrementString("counter", 1);
    }

    @Benchmark
    @Threads(100)
    public long incrementOneHundredClients() {
        return storage.incrementString("counter", 1);
    }
}
