# MyRedis Command Reference

MyRedis accepts the commands below through RESP2 arrays or the plain-text format. Responses shown are the logical values; RESP2 clients receive the corresponding RESP type.

## Server

| Command | Syntax | Result |
| --- | --- | --- |
| `PING` | `PING [message]` | `PONG`, or the supplied message |
| `INFO` | `INFO` | Operational counters, command latency, expiration, and runtime diagnostics |

## Strings and counters

| Command | Syntax | Result |
| --- | --- | --- |
| `SET` | `SET key value [EX seconds\|PX milliseconds]` | `OK` |
| `GET` | `GET key` | String value or null when missing |
| `DEL` | `DEL key [key ...]` | Number of keys deleted |
| `EXISTS` | `EXISTS key` | `1` or `0` |
| `INCR` | `INCR key` | New signed 64-bit integer value |
| `DECR` | `DECR key` | New signed 64-bit integer value |
| `INCRBY` | `INCRBY key increment` | New signed 64-bit integer value |

Counter commands create a missing key at the requested value, preserve an existing TTL, and reject non-integer values or signed 64-bit overflow.

## Lists

| Command | Syntax | Result |
| --- | --- | --- |
| `LPUSH` | `LPUSH key value [value ...]` | New list length |
| `RPUSH` | `RPUSH key value [value ...]` | New list length |
| `LPOP` | `LPOP key` | Removed value or null |
| `RPOP` | `RPOP key` | Removed value or null |
| `LRANGE` | `LRANGE key start stop` | Ordered array of values |
| `LLEN` | `LLEN key` | List length |

## Sets

| Command | Syntax | Result |
| --- | --- | --- |
| `SADD` | `SADD key member [member ...]` | Number of members added |
| `SREM` | `SREM key member [member ...]` | Number of members removed |
| `SMEMBERS` | `SMEMBERS key` | Sorted array of members |
| `SISMEMBER` | `SISMEMBER key member` | `1` or `0` |
| `SCARD` | `SCARD key` | Set cardinality |

## Hashes

| Command | Syntax | Result |
| --- | --- | --- |
| `HSET` | `HSET key field value [field value ...]` | Number of fields added |
| `HGET` | `HGET key field` | Field value or null |
| `HDEL` | `HDEL key field [field ...]` | Number of fields removed |
| `HGETALL` | `HGETALL key` | Array of field/value pairs |
| `HEXISTS` | `HEXISTS key field` | `1` or `0` |

## Sorted sets

| Command | Syntax | Result |
| --- | --- | --- |
| `ZADD` | `ZADD key score member [score member ...]` | Number of new members |
| `ZRANGE` | `ZRANGE key start stop` | Members ordered by score and member name |
| `ZSCORE` | `ZSCORE key member` | Score or null |
| `ZREM` | `ZREM key member [member ...]` | Number of members removed |
| `ZRANK` | `ZRANK key member` | Zero-based rank or null |

## Expiration

| Command | Syntax | Result |
| --- | --- | --- |
| `EXPIRE` | `EXPIRE key seconds` | `1` when applied, otherwise `0` |
| `PEXPIRE` | `PEXPIRE key milliseconds` | `1` when applied, otherwise `0` |
| `TTL` | `TTL key` | Remaining seconds, `-1` without TTL, or `-2` when missing/expired |
| `PERSIST` | `PERSIST key` | `1` when TTL removed, otherwise `0` |

## Common errors and limitations

- Wrong argument counts return an error response.
- Operations on an incompatible data type return a `WRONGTYPE` error.
- Empty keys are rejected.
- Numeric parsing, sorted-set scores, and overflow are validated.
- `MULTI`/`EXEC`, Pub/Sub, authentication, and full Redis command compatibility are not implemented.
- Maximum RESP value size, array size, and client connections are configurable; see [README.md](README.md).
