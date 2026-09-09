# Concurrency

MyRedis is designed for multiple clients operating on one shared in-memory
keyspace. The current implementation uses Java 21 virtual threads for client
handlers and concurrent collections inside the storage engine.

## Client lifecycle

`MyRedisServer` accepts sockets on one listener thread. Each accepted client is
registered against the configured connection limit and submitted to a virtual
thread-per-task executor. `ClientHandler` owns the socket streams and removes
the client from the registry in its `finally` block, including after malformed
input or unexpected command failures.

Shutdown closes the listener and registered sockets, stops the expiration
scheduler, waits briefly for client handlers, then closes persistence.

## Storage safety

The top-level keyspace uses `ConcurrentHashMap`. Atomic numeric operations use
`ConcurrentHashMap.compute`, so concurrent increments of one key do not lose
updates. Typed collection values are accessed through storage methods that
coordinate their per-value mutation and validate the Redis type before use.

Expiration checks happen before reads and writes. Conditional removal prevents
an old expiration decision from deleting a newer value written for the same
key.

## Persistence ordering

Successful mutating commands execute under the persistence mutation lock. The
mutation and its AOF append are therefore ordered with respect to other
mutations. Snapshot creation uses the same lock, preventing a snapshot from
observing a partially applied mutation sequence.

This is an in-process ordering guarantee. It is not a distributed transaction
protocol and does not provide replication or cross-process coordination.

## Expiration races

The active expiration scheduler samples expiry metadata independently of client
threads. Lazy access and scheduler cleanup can race safely because expiry
markers are removed conditionally. A key replaced after an old TTL is observed
is not removed by the stale cleanup operation.

## Evidence and limits

The test suite includes concurrent client load and atomic counter tests. The
JMH `ConcurrentStorageBenchmark` measures direct in-memory increments at one,
10, and 100 threads. These results are storage-engine measurements; they do
not represent TCP throughput or p95/p99 network latency.

The current design does not guarantee fairness across arbitrary commands,
bounded memory usage, multi-node consistency, or isolation semantics equivalent
to Redis transactions. Those capabilities require separate design work.
