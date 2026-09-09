# Storage Engine

`InMemoryStorageEngine` owns the process-local keyspace. Each key maps to a
`RedisObject` containing a `RedisType` tag and the corresponding Java value.
The engine validates key names, enforces type compatibility, performs lazy
expiration checks, and exposes replayable commands for snapshots.

## Data model

| Redis type | Java representation | Current behavior |
| --- | --- | --- |
| String | `String` | `SET`, `GET`, counters |
| List | `Deque<String>` | Push, pop, range, length |
| Set | `Set<String>` | Add, remove, membership, members, cardinality |
| Hash | `Map<String, String>` | Field updates, lookup, removal, enumeration |
| Sorted set | Ordered score/member structure | Add, range, score, remove, rank |

The top-level key map is a `ConcurrentHashMap`. Collection values are mutated
through storage methods, with synchronization where a compound collection
operation needs a consistent view. Sorted-set ordering is deterministic by
score and member name.

## Type and key rules

Empty keys are rejected. Missing keys return command-specific empty results.
Using a key with the wrong data type returns a `WRONGTYPE` error. Duplicate set
members and existing hash or sorted-set members are handled according to the
command semantics documented in [COMMANDS.md](../COMMANDS.md).

## Atomic counters

`INCR`, `DECR`, and `INCRBY` operate on signed 64-bit integer strings. The
per-key update uses a map compute operation so concurrent increments are
serialized atomically for that key. Invalid numeric values and overflow return
errors without replacing the existing value or creating an invalid state.

An existing counter TTL is preserved during numeric updates. A missing key is
created with the requested delta and no expiration.

## Expiration interaction

Expiration timestamps are maintained by `ExpirationManager`, not embedded in
`RedisObject`. Reads, existence checks, and mutations remove expired values
lazily. The active scheduler also samples expiry metadata and removes expired
keys that have not been accessed.

Replacing a value removes its previous expiration unless the operation is an
atomic counter update, which intentionally preserves the TTL. Snapshot commands
skip expired keys and preserve remaining TTLs with `PEXPIRE`.

## Snapshot representation

The engine does not serialize Java objects directly. It converts each live
value into replayable commands such as `SET`, `RPUSH`, `SADD`, `HSET`, or
`ZADD`. This keeps snapshot recovery on the same command semantics as normal
execution and makes the on-disk representation inspectable.

## Scalability tradeoffs

The engine is an in-memory single-process store. It has no eviction policy,
memory quota, sharding, replication, or secondary indexing. Snapshot generation
walks the keyspace and collection contents, so snapshot cost grows with dataset
size. These are explicit scope limitations, not production-scale guarantees.
