# Benchmarking

MyRedis benchmarks use [JMH](https://github.com/openjdk/jmh) against the direct
in-memory storage engine. They do not measure TCP, RESP decoding, command
parsing, logging, or persistence unless a future benchmark explicitly says so.

## Environment

The recorded baselines were collected with:

- JDK 21.0.10, OpenJDK 64-Bit Server VM
- JMH 1.37
- One fork
- One one-second warmup iteration
- Two one-second measurement iterations
- Compiler blackholes enabled by JMH

These short runs are useful for repository comparisons, but they are not a
substitute for a long, repeated performance study on production hardware.

## Running benchmarks

Compile the benchmark classes first:

```bash
mvn test-compile
```

Then run a benchmark with JMH and the test dependencies on the classpath:

```bash
java -cp "target/test-classes;<maven-dependency-classpath>" \
  com.myredis.benchmark.RunBenchmarks "StorageBenchmark.get"
```

Useful selections include:

```text
StorageBenchmark.get
StorageBenchmark.set
StorageBenchmark.increment
DatasetStorageBenchmark.getExistingKey
DatasetStorageBenchmark.setExistingKey
ConcurrentStorageBenchmark
```

Dataset benchmarks accept `100`, `1000`, `10000`, and `100000` keys. The
concurrency benchmark uses one, ten, and 100 JMH threads sharing one counter.

## Interpreting results

Throughput is reported in operations per microsecond and average time in
microseconds per operation. Results depend on the JVM, CPU, operating system,
garbage collector, compiler state, and benchmark parameters. Compare only runs
using the same methodology and record any changed environment details.

The current measured baselines are maintained in
[BENCHMARKS.md](../BENCHMARKS.md). Network latency, p95/p99 latency, memory
usage, and long-duration soak results are not yet part of the repository's
automated benchmark report. Runtime `INFO` reports approximate command p95 and
p99 latency using fixed upper-bound buckets; these operational percentiles are
separate from a future end-to-end benchmark report, which must measure TCP and
protocol overhead explicitly.
