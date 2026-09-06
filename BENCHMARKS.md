# MyRedis Benchmarks

The benchmark suite uses JMH and measures direct in-memory `GET` and `SET` operations.

Run the complete verification suite with:

```bash
mvn test
```

Run the benchmarks after compiling test classes with the JMH dependencies on the classpath:

```bash
mvn test-compile
java -cp "target/test-classes;<maven-dependency-classpath>" com.myredis.benchmark.RunBenchmarks
```

## Baseline

Collected on 2026-09-07 with JDK 21.0.10, OpenJDK 64-Bit Server VM, one fork,
one warmup iteration, and two one-second measurement iterations:

| Benchmark | Throughput |
| --- | ---: |
| `StorageBenchmark.get` | 133.985 ops/us |
| `StorageBenchmark.set` | 38.611 ops/us |

These numbers are environment-dependent and should only be compared with runs
using the same JVM, hardware, benchmark settings, and JMH version.
