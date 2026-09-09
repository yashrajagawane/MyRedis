# Testing

MyRedis uses Maven and JUnit 5 for automated tests. The suite exercises the
storage engine, command layer, protocol boundaries, server lifecycle,
configuration validation, expiration, concurrency, and persistence recovery.

## Verification commands

Run the full test suite:

```bash
mvn test
```

Build the packaged application:

```bash
mvn package
```

Compile benchmark classes without running the test suite:

```bash
mvn test-compile
```

## Test layers

### Unit tests

Storage tests cover strings, lists, sets, hashes, sorted sets, wrong types,
duplicate members, indexes, numeric operations, and expiration behavior.
Command tests cover parsing, argument errors, persistence-aware mutations,
metrics, TTL options, and command return values.

### Protocol and integration tests

Protocol tests cover RESP arrays, bulk values, null results, malformed input,
limits, partial reads, and response encoding. Server tests use real sockets and
exercise plain commands, RESP requests, pipelining, concurrent clients,
connection limits, and graceful shutdown.

### Persistence tests

Persistence tests cover fsync policies, AOF replay, incomplete final records,
mid-file corruption, snapshot offsets, snapshot TTLs, snapshot-plus-AOF-tail
recovery, empty values, missing persistence files, and controlled snapshot
failures.

### Concurrency and load tests

The load tests start a real server and many clients against shared state. Atomic
counter tests verify that concurrent increments preserve the exact final value.
JMH benchmarks separately measure direct in-memory performance and are not part
of the default Maven test run.

## Regression policy

Every discovered correctness or reliability bug should receive a focused
regression test. Tests should assert observable behavior rather than internal
implementation details where practical. New command behavior must update both
the command reference and protocol compatibility documentation.

## Current limitations

The suite does not yet provide long-duration soak tests, systematic disk-full
simulation, process-level crash injection, p95/p99 network latency reporting,
memory profiling, or automated Redis client compatibility across a broad
command matrix. These are planned quality improvements rather than silently
claimed guarantees.
