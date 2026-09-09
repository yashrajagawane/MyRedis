# Protocol

MyRedis accepts two request formats on each TCP connection:

- RESP2 arrays of bulk strings, which is the format used by `redis-cli`.
- A plain-text command line terminated by LF or CRLF, useful for manual testing.

Each connection may send multiple commands without reconnecting. The handler
reads one complete request, executes it, writes one response, and continues;
this supports pipelined requests because the client can send several commands
before reading the responses.

## RESP2 requests

Commands are expected as arrays whose elements are bulk strings:

```text
*2\r\n$4\r\nPING\r\n$5\r\nhello\r\n
```

The decoder handles partial TCP reads by buffering input until each complete
line and bulk payload is available. It rejects empty command arrays, null
command arguments, non-bulk command elements, invalid line endings, incomplete
bulk strings, and oversized protocol lines.

## RESP2 responses

The encoder currently produces these RESP2 types:

| Logical result | RESP2 type |
| --- | --- |
| `PING`, successful `SET` | Simple string |
| Integer-returning commands | Integer |
| String values and `INFO` | Bulk string |
| Missing values | Null bulk string |
| List/set/hash/sorted-set range results | Array of bulk strings |
| Command failures | Error |

Nested response arrays and arbitrary client-side RESP values are outside the
current command model. Requests are command arrays of bulk strings rather than
general RESP2 data structures.

## Limits

The decoder defaults to a maximum bulk/plain value size of 16 MiB and a maximum
command-array size of 1,024 elements. Both are configurable through
`limits.max.value.bytes` and `limits.max.array.elements`. The protocol line
metadata limit is 128 bytes. These limits protect the server from unbounded
allocation during malformed or hostile requests.

## Compatibility scope

The supported command set is documented in [COMMANDS.md](../COMMANDS.md).
Compatibility should be considered command-by-command: a command is only
treated as compatible after its return types, errors, edge cases, and pipelined
behavior have been tested. Full Redis protocol and command compatibility is not
claimed.
