# Configuration

MyRedis loads configuration once during startup and creates an immutable
`ServerConfig`. The precedence order is:

```text
built-in defaults
    -> myredis.conf (or --config <path>)
    -> MYREDIS_* environment variables
    -> CLI options
```

Later sources override earlier sources. Invalid values fail fast before the
server starts.

## Settings

| Setting | Default | Environment variable | Purpose |
| --- | --- | --- | --- |
| `host` | `127.0.0.1` | `MYREDIS_HOST` | Bind address; Docker's checked-in config explicitly uses `0.0.0.0` |
| `port` | `6379` | `MYREDIS_PORT` | TCP port; `0` selects an ephemeral port for tests |
| `aof.enabled` | `true` | `MYREDIS_AOF_ENABLED` | Enable AOF and snapshot persistence |
| `aof.path` | `data/myredis.aof` | `MYREDIS_AOF_PATH` | AOF file path |
| `aof.fsync` | `ALWAYS` | `MYREDIS_AOF_FSYNC` | `ALWAYS`, `EVERY_SECOND`, or `NEVER` |
| `snapshot.path` | `data/myredis.snapshot` | `MYREDIS_SNAPSHOT_PATH` | Snapshot file path |
| `snapshot.interval.seconds` | `60` | `MYREDIS_SNAPSHOT_INTERVAL_SECONDS` | Periodic snapshot interval; `0` disables periodic snapshots |
| `log.level` | `INFO` | `MYREDIS_LOG_LEVEL` | `TRACE`, `DEBUG`, `INFO`, `WARN`, or `ERROR` |
| `limits.max.value.bytes` | `16777216` | `MYREDIS_MAX_VALUE_BYTES` | Maximum bulk/plain value size |
| `limits.max.array.elements` | `1024` | `MYREDIS_MAX_ARRAY_ELEMENTS` | Maximum RESP command-array length |
| `limits.max.connections` | `10000` | `MYREDIS_MAX_CONNECTIONS` | Maximum accepted client connections |

## Examples

Use a custom configuration file:

```bash
java -jar target/myredis-0.1.0-SNAPSHOT.jar --config ./myredis.conf
```

Override settings from the command line:

```bash
java -jar target/myredis-0.1.0-SNAPSHOT.jar --port 6380 --aof-fsync EVERY_SECOND
```

Use environment variables in a container or service definition:

```bash
MYREDIS_HOST=127.0.0.1 \
MYREDIS_MAX_CONNECTIONS=500 \
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

## Validation and safety

The port must be between 0 and 65,535. Numeric limits and connection counts
must be positive, snapshot intervals cannot be negative, booleans must be
`true` or `false`, and enum-like values are checked against their supported
sets. Unknown CLI options and missing option values are rejected.

Persistence paths are passed to Java NIO and parent directories are created by
the persistence writers when needed. Operators should still choose paths with
appropriate ownership and permissions; MyRedis does not provide path sandboxing
or authentication.

Docker copies `myredis.conf` into the image and stores persistence data under
`/app/data`. Mount a persistent volume at that directory when data must survive
container replacement.
