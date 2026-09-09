package com.myredis.benchmark;

import com.myredis.storage.InMemoryStorageEngine;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Measures direct in-memory operations across increasing keyspace sizes. */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class DatasetStorageBenchmark {
    @Param({"100", "1000", "10000", "100000"})
    private int datasetSize;

    private InMemoryStorageEngine storage;
    private String selectedKey;

    @Setup
    public void setup() {
        storage = new InMemoryStorageEngine();
        for (int index = 0; index < datasetSize; index++) {
            storage.setString("key-" + index, "value-" + index);
        }
        selectedKey = "key-" + (datasetSize / 2);
    }

    @Benchmark
    public String getExistingKey() {
        return storage.getString(selectedKey).orElseThrow();
    }

    @Benchmark
    public void setExistingKey() {
        storage.setString(selectedKey, "updated");
    }
}
