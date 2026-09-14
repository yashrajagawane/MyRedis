# Compatibility

MyRedis is Redis-inspired and RESP2-compatible for the command subset listed
below. It does not claim full Redis compatibility. Behavior should be judged by
the tested command semantics and return types in this repository.

## Supported command matrix

| Area | Commands | RESP2 coverage |
| --- | --- | --- |
| Server | `PING`, `QUIT`, `INFO` | Simple/bulk responses, clean client close, and diagnostics |
| Transactions | `MULTI`, `EXEC`, `DISCARD` | Per-client queueing, ordered execution arrays, and discard |
| Strings | `SET`, `GET`, `DEL`, `EXISTS` | Bulk, null-bulk, and integer responses |
| Counters | `INCR`, `DECR`, `INCRBY` | Integer responses, atomic per-key updates |
| Lists | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN` | Integer, bulk/null, and array responses |
| Sets | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`, `SCARD` | Integer and array responses |
| Hashes | `HSET`, `HGET`, `HDEL`, `HGETALL`, `HEXISTS` | Integer, bulk/null, and array responses |
| Sorted sets | `ZADD`, `ZRANGE`, `ZSCORE`, `ZREM`, `ZRANK` | Integer, bulk/null, and array responses |
| Expiration | `EXPIRE`, `PEXPIRE`, `TTL`, `PERSIST` | Integer responses and persisted TTLs |

## Tested wire behavior

The protocol and server tests cover RESP command arrays of bulk strings,
partial TCP reads, multiple commands per connection, pipelined requests,
malformed requests, null results, arrays, errors, and configurable payload
limits. Plain-text commands are also supported for manual use.

## Semantic boundaries

The implementation intentionally differs from a promise of full Redis behavior
in several areas:

- `INFO` exposes MyRedis-specific operational counters.
- Sorted-set ordering and command options are limited to the documented subset.
- Responses are encoded from the command-specific response mapping rather than
  from a general Redis value protocol.
- Nested request arrays, Pub/Sub, authentication, ACLs,
  replication, clustering, and TLS are not implemented.
- Redis command aliases and options not listed in [COMMANDS.md](../COMMANDS.md)
  should be treated as unsupported.

## Compatibility testing policy

Commands are considered supported only when their success responses, missing
values, wrong-type behavior, argument errors, numeric edge cases, expiration
interaction where relevant, and pipelined behavior have tests. A future Redis
CLI compatibility matrix should record the client version, command, expected
response, observed response, and any intentional difference.
