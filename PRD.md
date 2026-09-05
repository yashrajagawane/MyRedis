# MyRedis — Product Requirements Document (PRD)

> Companion docs: [Architecture.md](./Architecture.md) · [Design.md](./Design.md) · [Phases.md](./Phases.md)

## 1. Overview
MyRedis is a Redis-inspired, eventually Redis-compatible in-memory key-value database, built entirely from scratch in **Java 21**, without relying on any third-party library that implements core database or protocol logic. It is a **learning-first systems project**: the explicit goal is depth of understanding of how an in-memory database and its network protocol actually work, not to reproduce Redis feature-for-feature or ship a production system.

## 2. Motivation / Problem Statement
Typical backend/CRUD projects (framework + ORM + UI) don't teach systems fundamentals — TCP networking, wire protocols, concurrency primitives, data-structure design under contention, durability/recovery, and performance measurement. MyRedis exists to close that gap and produce a portfolio project that demonstrates systems-level engineering ability rather than framework glue.

## 3. Goals
- Implement a functioning TCP server that speaks a Redis-like, and eventually RESP2-compatible, protocol.
- Implement core Redis data types with correct semantics: String, List, Set, Hash, Sorted Set.
- Implement TTL/expiration with correct behavior under concurrency.
- Implement durability via AOF and periodic snapshots, with correct startup recovery.
- Demonstrate safe, understandable concurrency — hand-built, not framework-provided.
- Deliver a tested, benchmarked, documented, containerized final artifact.
- Build in strictly incremental phases with explicit checkpoints, so each phase is independently understandable, working, and reviewable before the next begins.

## 4. Non-Goals (explicitly out of scope for v1)
- Full Redis command-set parity — this is not a production-grade Redis clone.
- Redis Cluster, replication, or Sentinel.
- Scripting (Lua), pub/sub, ACL/auth, RESP3 — possible stretch goals only if explicitly requested later.
- Using Spring Boot, Jedis, Lettuce, or any existing Redis implementation for core logic.

## 5. Target Audience
- **Primary:** the developer, as a learning project and portfolio piece.
- **Secondary:** recruiters/reviewers assessing backend/systems engineering ability from the GitHub repo.
- **Tertiary:** anyone using a standard RESP2 client (e.g. `redis-cli`) to issue basic commands once Phase 8 lands.

## 6. Success Criteria / Definition of Done (v1)
MyRedis v1 is "done" when:
1. A standard `redis-cli` (or any RESP2 client) can connect and correctly run `PING`, `SET`, `GET`, `DEL`, `EXISTS`, `EXPIRE`, `TTL`, `PERSIST` against it.
2. Strings, Lists, Sets, Hashes, and Sorted Sets all support their core operations with correct semantics under concurrent access.
3. Data survives a server restart via AOF and/or snapshot recovery, including TTLs.
4. The server handles many simultaneous client connections without data races, lost updates, or corruption.
5. Unit, integration, concurrency, and persistence-recovery tests pass; JMH benchmarks exist for GET/SET.
6. The project has a config file, structured logging, a Dockerfile, and a portfolio-quality README with architecture docs.

## 7. Functional Requirements

| Category      | Commands (v1 target)                                         |
|---------------|----------------------------------------------------------------|
| Connection    | `PING`                                                          |
| Generic       | `DEL`, `EXISTS`, `EXPIRE`, `TTL`, `PERSIST`                     |
| String        | `SET` (incl. `EX`/`PX` options), `GET`                          |
| List          | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN`              |
| Set           | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`, `SCARD`                |
| Hash          | `HSET`, `HGET`, `HDEL`, `HGETALL`, `HEXISTS`                    |
| Sorted Set    | `ZADD`, `ZRANGE`, `ZSCORE`, `ZREM`, `ZRANK`                     |

Each command must:
- Validate argument count/shape and return a clear protocol-level error on mismatch.
- Return a `WRONGTYPE`-style error when applied to a key holding a different data type.
- Behave identically whether reached over the plain-text parser (Phases 2–7) or full RESP2 (Phase 8+).

## 8. Non-Functional Requirements
- **Concurrency:** correct behavior under N simultaneous clients (target: hundreds of concurrent connections on dev hardware).
- **Performance:** baseline GET/SET latency and throughput measured via JMH — no fixed numeric target beyond "trend visibly improves as locking is refined and is documented, not guessed."
- **Durability:** no acknowledged write is lost if AOF is enabled and its fsync policy is respected.
- **Maintainability:** SOLID, small focused classes, no unnecessary abstraction, each package independently unit-testable.
- **Observability:** structured logs via SLF4J/Logback — INFO for lifecycle, DEBUG for command tracing, WARN/ERROR for failures.
- **Portability:** runs as a single JAR and as a Docker container with no code changes.

## 9. Technology Constraints

| Allowed | Disallowed |
|---|---|
| Java 21, Maven | Spring Boot |
| JUnit 5 | Spring Data Redis |
| Java standard library | Jedis |
| `java.net` Socket / NIO | Lettuce |
| `ExecutorService` / `java.util.concurrent` | Any existing Redis implementation |
| SLF4J + Logback | Any library implementing core DB/protocol logic |
| JMH | |
| Docker | |

## 10. Assumptions
- Single-node only — no clustering or replication.
- Development/grading environment has Java 21 and Maven, or Docker.
- Client compatibility target is **RESP2**, not RESP3, for v1.

## 11. Risks & Mitigations
| Risk | Mitigation |
|---|---|
| Premature complexity (NIO reactor, lock-free structures) stalls progress | Start with the simplest correct concurrency model (thread-per-connection via virtual threads + `ConcurrentHashMap`); optimize only if benchmarks justify it |
| AOF/snapshot format drifts from the live command set | Version the persistence format from Phase 7 onward |
| Scope creep toward full Redis parity | This PRD's Non-Goals section is authoritative; new commands require explicit request |
| Concurrency bugs hidden by low test contention | Dedicated concurrency test suite (Phase 4 onward) with realistic thread counts, not just happy-path unit tests |

## 12. Milestones
See **[Phases.md](./Phases.md)** for the full phase-by-phase roadmap with steps and Definition of Done per phase. High-level milestones: TCP server → command parsing → in-memory storage → concurrency → data types → TTL → persistence → RESP protocol → testing/benchmarking → production polish.

## 13. Open Questions
Resolved at the relevant phase, not before — see **Design.md §13 (Design Alternatives Considered)**:
- Virtual threads vs. fixed thread pool vs. NIO for Phase 4.
- AOF format: RESP-encoded log vs. simple line-based log.
- Snapshot format: custom versioned binary vs. Java serialization vs. plain text.
