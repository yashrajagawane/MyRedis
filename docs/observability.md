# Observability

MyRedis provides lightweight process-local diagnostics through the `INFO`
command and structured logging through SLF4J/Logback. The implementation uses
JDK atomics and adders rather than an external metrics framework.

## INFO metrics

`INFO` currently reports:

| Field | Meaning |
| --- | --- |
| `connected_clients` | Currently registered client connections |
| `commands_processed` | Commands entering command execution |
| `command_latency_avg_us` | Average measured command execution time in microseconds |
| `command_latency_p95_us` | Approximate 95th-percentile command latency using a fixed upper-bound bucket |
| `command_latency_p99_us` | Approximate 99th-percentile command latency using a fixed upper-bound bucket |
| `command_latency_max_us` | Maximum measured command execution time in microseconds |
| `expired_keys` | Successful expiry-marker removals |
| `aof_writes` | Successful AOF appends |
| `snapshots` | Successful snapshot replacements |
| `persistence_errors` | AOF, snapshot, or recovery failures |
| `keyspace_hits` | Successful string `GET` lookups |
| `keyspace_misses` | Missing string `GET` lookups |
| `command_<name>` | Count for each registered command name |

Metrics are process-local and reset when the server restarts. Latency includes
command execution and, for mutating commands, the persistence append performed
by the command path. It does not include socket write time after the command
returns.

## Logging

The server logs lifecycle events, client connections, malformed or failed
commands, persistence recovery, and shutdown behavior. `log.level` supports
`TRACE`, `DEBUG`, `INFO`, `WARN`, and `ERROR`. Log output should be treated as
diagnostic context rather than a durable audit trail.

## Operational use

Use `INFO` to inspect a running local or containerized server:

```text
INFO
```

Compare `connected_clients` with the configured connection limit, monitor
`persistence_errors` for failures, and use command counts plus latency fields
to identify workload changes. A nonzero `expired_keys` value indicates cleanup
activity, not necessarily a problem.

## Current gaps

There is no Prometheus endpoint, OpenTelemetry exporter, structured JSON log
schema, per-command latency histogram, memory gauge, CPU gauge,
or durable audit event stream. Metrics also do not distinguish every storage
operation or report network queueing. These should be added only with a clear
deployment requirement and tests for their overhead and correctness.
