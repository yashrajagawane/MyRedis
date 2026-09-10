# Design Decisions

This document records the reasoning behind the current implementation. It is
intended to make future changes deliberate and reviewable.

## Java 21 and virtual threads

The server uses Java 21 virtual threads for one handler per client. This keeps
blocking socket code easy to understand while avoiding one expensive platform
thread per mostly-idle connection. The tradeoff is that virtual threads do not
remove the need for connection, payload, and persistence limits.

## Concurrent hash map at the keyspace boundary

The top-level store uses `ConcurrentHashMap` because independent keys should be
available concurrently and atomic per-key operations can use `compute`. The
design avoids synchronizing the entire database for ordinary reads. Compound
collection operations still use targeted synchronization where a consistent
value-level mutation requires it.

## Separate expiration metadata

TTL state lives in `ExpirationManager` rather than inside every `RedisObject`.
This keeps value-type code independent from deadline bookkeeping and lets lazy
access and active cleanup share one authoritative expiry map. The tradeoff is a
second map lookup and eventual, rather than deadline-exact, active cleanup.

## Command-based snapshots

Snapshots store replayable commands instead of Java serialization. This keeps
the file inspectable, avoids coupling persistence to object implementation
details, and makes snapshot recovery use the same command semantics as normal
execution. The tradeoff is that snapshot generation and recovery do more
encoding and command dispatch work.

## AOF plus snapshots

The AOF provides an ordered mutation history while snapshots bound recovery
time. The snapshot records an AOF offset so only the tail after the snapshot is
replayed. Configurable fsync policies make the durability/performance tradeoff
explicit. This is local persistence, not replication or a distributed log.

## RESP2 with a plain-text diagnostic path

RESP2 is the compatibility path used by Redis clients. The plain-text path is
kept because it makes manual testing and teaching the command layer convenient.
Both paths converge before command execution, so storage semantics do not
depend on the wire format.

## Blocking networking instead of NIO

A blocking `ServerSocket` and virtual-thread handlers are simpler than a
selector-based reactor for this single-process project. The design keeps
networking ownership clear and is sufficient for the current scope. NIO,
backpressure controls, and idle timeouts remain possible future work if
measurements demonstrate a need.

## Lightweight metrics instead of a metrics dependency

`ServerMetrics` uses JDK atomics and adders and exposes diagnostics through
`INFO`. This avoids adding a metrics framework before there is a deployment
requirement for one. The tradeoff is a small process-local metric surface
rather than Prometheus/OpenTelemetry integration.

## Explicit compatibility boundaries

MyRedis documents supported commands and return types rather than claiming full
Redis compatibility. Transactions, Pub/Sub, authentication, replication,
clustering, and complete RESP data-model support require separate designs and
tests before they should be introduced or advertised.
