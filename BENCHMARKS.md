# MyRedis Benchmarks

The benchmark suite uses JMH and measures direct in-memory `GET`, `SET`, atomic `INCR`,
and dataset-size-sensitive operations.

Run the complete verification suite with:

```bash
mvn test
```

Run the benchmarks after compiling test classes with the JMH dependencies on the classpath:

```bash
mvn test-compile
java -cp "target/test-classes;<maven-dependency-classpath>" com.myredis.benchmark.RunBenchmarks
```

`DatasetStorageBenchmark` covers 100, 1,000, 10,000, and 100,000 keys through
`getExistingKey` and `setExistingKey`. Collect results separately for each
dataset size; no dataset-size result is claimed until it has been run on the
target hardware.

## Baseline

Collected on 2026-09-07 with JDK 21.0.10, OpenJDK 64-Bit Server VM, one fork,
one warmup iteration, and two one-second measurement iterations:

| Benchmark | Throughput |
| --- | ---: |
| `StorageBenchmark.get` | 133.985 ops/us |
| `StorageBenchmark.set` | 38.611 ops/us |
| `StorageBenchmark.increment` | 14.281 ops/us |
| `DatasetStorageBenchmark.getExistingKey` (100 keys) | 53.789 ops/us |

The counter benchmark also measured `0.067 us/op` average time, while the
100-key dataset lookup measured `0.013 us/op` average time under the same
settings. The runs used JMH 1.37 with compiler blackholes enabled and one
thread on JDK 21.0.10.

These numbers are environment-dependent and should only be compared with runs
using the same JVM, hardware, benchmark settings, and JMH version.
