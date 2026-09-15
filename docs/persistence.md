# Persistence

MyRedis uses two complementary persistence mechanisms:

- An append-only file (AOF) records successful mutating commands.
- A snapshot stores a replayable representation of the current keyspace and the
  AOF byte offset used to create it.

Persistence is enabled by default and can be disabled with `aof.enabled=false`.

## AOF writes

Commands execute against memory first. If the command succeeds, the command
name and original arguments are encoded as one line in the AOF. Failed commands
are not recorded. A fair mutation lock orders concurrent mutations and their
corresponding AOF records.

The configured fsync policy controls durability:

| Policy | Behavior |
| --- | --- |
| `ALWAYS` | Flushes and forces the file channel after each append |
| `EVERY_SECOND` | Flushes and forces from a scheduled one-second task |
| `NEVER` | Flushes buffered output but does not force each append |

`close()` flushes and forces the writer for all policies. A successful AOF
append increments the `aof_writes` value reported by `INFO`; append failures
increment `persistence_errors` and fail the mutation.

## Snapshots

Snapshots are written to a temporary file in the target directory. The writer
then forces the temporary file and replaces the configured snapshot path with
an atomic move when the filesystem supports it, falling back to replacement
move when atomic move is unavailable.

The snapshot contains:

1. A version marker.
2. An `AOF_OFFSET` byte position.
3. Replayable commands for the current keyspace.
4. Remaining TTLs represented as `PEXPIRE` commands.

Expired keys are removed or skipped while snapshot commands are generated.
Successful snapshots increment the `snapshots` metric.

## Recovery

Startup recovery follows this order:

```text
load snapshot if present
    -> validate snapshot version and AOF offset
    -> replay AOF records after that offset
    -> start with empty state when both files are absent
```

An offset below zero or beyond the current AOF length is rejected. A malformed
record before the final AOF record fails recovery. If the final record is
incomplete, the replayer truncates the incomplete tail and keeps earlier valid
records. Recovery failures increment `persistence_errors` and are surfaced to
the caller.

Persistence shutdown is idempotent. The first `close()` stops scheduled
snapshots, writes the final snapshot, flushes and closes the AOF, and records
any finalization error. Repeated close calls are safe and do not reuse closed
file resources.

## Guarantees and limitations

The current design provides ordered successful mutation logging, configurable
fsync behavior, atomic snapshot replacement, deterministic snapshot-plus-AOF
tail recovery, and explicit corruption handling. It does not provide replicated
durability, checksums, point-in-time backups, encrypted persistence, or
cross-process locking. Operators should protect persistence files with normal
filesystem permissions and backups appropriate to their deployment.
