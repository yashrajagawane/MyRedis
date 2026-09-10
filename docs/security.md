# Security

MyRedis is an educational Redis-compatible server with several resource
exhaustion protections, but it is not yet a hardened internet-facing service.
Deploy it on a trusted network or behind an authenticated, access-controlled
network boundary.

## Current protections

- RESP bulk values and plain-text commands have configurable maximum sizes.
- RESP command arrays have a configurable maximum element count.
- Accepted client connections are bounded by `limits.max.connections`.
- Invalid configuration values and unknown CLI options fail before startup.
- Malformed protocol input is rejected per connection and does not terminate the
  server process.
- Persistence writes use configured filesystem paths and atomic snapshot
  replacement where supported.
- The Docker image runs the server as the non-root `myredis` user.
- The Docker image declares SIGTERM as its stop signal and uses container-aware
  JVM memory sizing.
- Docker persistence is isolated under the `/app/data` volume.

## Deployment guidance

The default bind address is `0.0.0.0`, which exposes the listener on all
interfaces. Set `host=127.0.0.1` for local-only operation, or restrict access
with a firewall, private network, or authenticated proxy. Do not expose the
default unauthenticated server directly to the public internet.

Choose persistence paths owned by the service account and restrict filesystem
permissions. Avoid placing AOF or snapshot files in directories shared with
untrusted processes. Back up persistence files through controlled operational
procedures rather than exposing them over the network.

## Current limitations

MyRedis currently has no `AUTH` command, TLS, ACLs, encryption at rest,
multi-tenant isolation, audit log, replication authentication, or network-level
command allowlist. It also does not provide a memory quota or a request-rate
limiter. These limitations are intentional scope boundaries and must be
considered in any deployment threat model.

## Security testing priorities

Future hardening should add tests for oversized requests, connection flooding,
malformed RESP sequences, persistence path permissions, client disconnects,
unexpected handler exceptions, and long-running resource consumption. Any new
security control should be configurable, fail closed where practical, and be
covered by regression tests.
