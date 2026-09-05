# MyRedis — Phased Development Roadmap

> Companion docs: [PRD.md](./PRD.md) · [Architecture.md](./Architecture.md) · [Design.md](./Design.md)

## How to read this document
Each phase lists a **Goal**, **Why it matters**, **Deliverables**, **Detailed steps**, and a **Definition of Done**. Phases are implemented strictly in order, one at a time. **The next phase is never started until it is explicitly requested** — this file is the authoritative expansion of that rule, not an override of it.

---

## Phase 0 — Workspace Inspection *(part of Phase 1)*
Before any code is written: inspect the workspace. If empty, initialize only as much of the Maven skeleton as Phase 1 needs — do not pre-create packages reserved for later phases.

---

## Phase 1 — Project Setup & TCP Server
**Goal:** A runnable Java 21/Maven project with a bare TCP server that accepts connections on port 6379, acknowledges input, and shuts down gracefully.
**Why:** Establishes the process, build, and networking skeleton everything else plugs into; proves the toolchain before any database logic exists.
**Deliverables:** `pom.xml`, `MyRedisApplication`, `com.myredis.server.MyRedisServer`, `com.myredis.server.ClientHandler`, stub `README.md`, `.gitignore`.
**Steps:**
1. `pom.xml` with Java 21 target and only JUnit 5 + SLF4J/Logback dependencies (no command/storage logic yet).
2. `MyRedisServer`: bind a `ServerSocket`, accept loop on its own thread, hand each socket to a `ClientHandler`.
3. `ClientHandler`: read raw input, write a trivial acknowledgement/echo (real parsing is Phase 2).
4. Graceful shutdown: a shutdown hook or explicit `stop()` closes the server socket and any open client sockets cleanly.
5. Manual test via `nc localhost 6379` or `telnet`.
**Definition of Done:** Server starts, accepts a raw TCP connection, responds to raw input, and shuts down with no hanging threads or exceptions; a basic smoke test passes.

---

## Phase 2 — Command Parser
**Goal:** Turn raw client input into structured `Command` objects for `PING`, `SET`, `GET`, `DEL`, `EXISTS`.
**Why:** Separates "what the client said" from "what we do about it," so every later command is additive, not a rewrite.
**Deliverables:** `com.myredis.command.{Command, CommandContext, CommandResult, CommandParser, CommandRegistry}`, one class per command (execution still stubbed — real storage isn't wired until Phase 3).
**Steps:**
1. Define the `Command` interface and `CommandResult`.
2. `CommandParser`: whitespace-tokenized parsing (RESP arrives in Phase 8) → command name + args.
3. `CommandRegistry`: name → command factory map; unknown command → error `CommandResult`.
4. Wire `ClientHandler` to call parser → dispatcher → (stub) result → plain-text response, replacing Phase 1's echo.
**Definition of Done:** Sending `PING`, `SET k v`, `GET k`, `DEL k`, `EXISTS k` over a raw socket returns structurally correct responses (even if not yet persisted); unit tests cover the parser and each command's argument validation.

---

## Phase 3 — In-Memory Storage
**Goal:** Real, correct single-threaded storage for String values behind the Phase 2 commands.
**Why:** First point where MyRedis actually *stores* something; validates the command ↔ storage boundary.
**Deliverables:** `com.myredis.storage.{StorageEngine, InMemoryStorageEngine, RedisObject, RedisType}` (STRING only for now).
**Steps:**
1. `StorageEngine` interface with `setString` / `getString` / `delete` / `exists`.
2. `InMemoryStorageEngine` backed by a plain `HashMap` (thread safety is Phase 4's concern).
3. Wire `SetCommand` / `GetCommand` / `DelCommand` / `ExistsCommand` to the real engine via `CommandContext`.
**Definition of Done:** `SET`/`GET`/`DEL`/`EXISTS` behave correctly for String values in a single-client session; unit tests cover the storage engine directly.

---

## Phase 4 — Concurrent Clients
**Goal:** Many clients simultaneously, with thread-safe storage and a clean connection lifecycle.
**Why:** A "database" that only handles one client at a time isn't one; this is where correctness-under-concurrency becomes the central concern for the rest of the project.
**Deliverables:** Thread-pool wiring in `MyRedisServer` (virtual threads recommended per Design.md §10 — to confirm before implementing), `ConnectionRegistry`, `InMemoryStorageEngine` upgraded to `ConcurrentHashMap`, per-connection error isolation.
**Steps:**
1. Replace ad hoc thread creation with an `ExecutorService` (virtual-thread-per-task recommended; confirm before implementing).
2. Swap storage backing to `ConcurrentHashMap`.
3. Add `ConnectionRegistry` to track and close all connections on shutdown.
4. Ensure one client's malformed input or exception never crashes another client's connection or the server.
**Definition of Done:** N concurrent clients (e.g. a 50–100 thread test harness) can `SET`/`GET`/`DEL` without data races, lost updates, or server crashes; a concurrency test proves it.

---

## Phase 5 — Redis Data Types
**Goal:** Lists, Sets, Hashes, and Sorted Sets, added **one type at a time**, not all at once.
**Why:** This is the bulk of "database" functionality; doing it type-by-type keeps each addition reviewable.

| Sub-phase | Type | Commands | Backing structure |
|---|---|---|---|
| 5a | List | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN` | `Deque<String>` |
| 5b | Set | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`, `SCARD` | `Set<String>` |
| 5c | Hash | `HSET`, `HGET`, `HDEL`, `HGETALL`, `HEXISTS` | `Map<String,String>` |
| 5d | Sorted Set | `ZADD`, `ZRANGE`, `ZSCORE`, `ZREM`, `ZRANK` | score-ordered structure |

**Steps (repeated per sub-phase):** extend `RedisType` → add the type's commands → add `WrongTypeException` checks → add per-type storage methods → add unit + concurrency tests → only then move to the next sub-phase.
**Definition of Done:** Each sub-phase independently passes its own tests and a manual smoke test before the next sub-phase begins; `WRONGTYPE`-style errors are correct when a command targets the wrong type.

---

## Phase 6 — Expiration & TTL
**Goal:** `EXPIRE`, `TTL`, `PERSIST`, `SET` with expiration options, and automatic (lazy + active) expiration.
**Why:** TTL correctness under concurrency is one of the trickiest parts of a real key-value store — deliberately sequenced after data types are stable.
**Deliverables:** `com.myredis.expiration.{ExpirationManager, ExpirationScheduler}`, updated `StorageEngine` reads that consult expiry.
**Steps:**
1. `ExpirationManager` with `setExpiry` / `getTtl` / `persist` / `isExpired`.
2. Lazy-expiration hook in every read path.
3. `ExpirationScheduler` background sampler for active expiration.
4. `EXPIRE`, `TTL`, `PERSIST` commands; `SET ... EX/PX` option parsing.
**Definition of Done:** Keys expire correctly under concurrent read/write load; `TTL` reports accurate remaining time; expired keys are unreachable and eventually reclaimed even without being read.

---

## Phase 7 — Persistence
**Goal:** Durable restart via AOF and periodic snapshots.
**Why:** An in-memory store that loses everything on restart isn't durable enough to call a database — this is the "does it survive a crash" milestone.
**Deliverables:** `com.myredis.persistence.{AofWriter, AofReplayer, SnapshotWriter, SnapshotLoader, PersistenceManager}`, startup recovery wiring in `MyRedisApplication`.
**Steps:**
1. `AofWriter`: append every mutating command post-execution, with a configurable fsync policy.
2. `AofReplayer`: replay the AOF through the real `CommandParser`/dispatcher path on startup.
3. `SnapshotWriter`/`SnapshotLoader`: versioned full-keyspace dump/load, triggered periodically and on graceful shutdown.
4. Startup recovery order: snapshot → AOF tail → empty.
**Definition of Done:** Killing and restarting the server (with AOF/snapshot enabled) restores the exact prior keyspace, including TTLs; an automated persistence-recovery test proves it.

---

## Phase 8 — RESP Protocol
**Goal:** Real RESP2 request/response support so standard Redis clients (e.g. `redis-cli`) can talk to MyRedis for every command implemented so far.
**Why:** This is the "Redis-compatible" milestone — it validates that the protocol layer was properly isolated since Phase 1.
**Deliverables:** `com.myredis.protocol.{RespType, RespDecoder, RespEncoder, ProtocolException}`; `ClientHandler` and `CommandParser` updated to consume RESP arrays instead of whitespace-split text.
**Steps:**
1. Implement `RespDecoder` for all five RESP2 types, with requests parsed as arrays of bulk strings.
2. Implement `RespEncoder`, mapping each `CommandResult` shape to the correct RESP reply type.
3. Swap `ClientHandler`'s decode/encode calls to the new protocol classes; confirm the whitespace-parser path is fully replaced (or explicitly kept only as an opt-in debug mode).
**Definition of Done:** `redis-cli -p 6379` can connect and successfully run every command implemented in Phases 2–6; integration tests send real RESP bytes and assert real RESP bytes back.

---

## Phase 9 — Testing & Benchmarking
**Goal:** A trustworthy, measured test/benchmark suite covering everything built so far.
**Why:** Closes the loop on "does it actually work and how fast is it," making the earlier phases' claims verifiable rather than assumed.
**Deliverables:** Complete unit/integration/concurrency/persistence-recovery test suites; JMH benchmark classes for GET/SET latency and throughput.
**Steps:**
1. Audit and fill any test gaps left by earlier phases.
2. Add JMH benchmark classes; document baseline numbers.
3. Add a simple load-test script/class exercising many concurrent clients.
**Definition of Done:** `mvn test` is green end-to-end; JMH benchmark results are captured and documented (e.g. in a `BENCHMARKS.md` or a README section).

---

## Phase 10 — Production Polish
**Goal:** Make the repository itself portfolio- and deployment-ready.
**Why:** Matches the project's stated care for professional, GitHub-ready presentation, not just working code.
**Deliverables:** Finalized `myredis.conf`/config support, finalized `logback.xml`, `Dockerfile`, polished `README.md`, this Architecture/Design/Phases/PRD set kept current, a versioned release (e.g. `v1.0.0`), architecture diagrams embedded in the README.
**Steps:**
1. Finalize `ServerConfig`/`ConfigLoader` (file + CLI/env overrides).
2. Finalize logging levels/format for real use, not just development.
3. Verify the multi-stage `Dockerfile` end-to-end (`docker build` + `docker run -p 6379:6379`).
4. Rewrite `README.md` to portfolio standard: overview, architecture diagram, quick start, command reference, benchmarks, roadmap.
5. Tag a `v1.0.0` release once everything above is verified.
**Definition of Done:** A stranger can `docker run` the image (or `mvn package && java -jar`) and use MyRedis via `redis-cli` with no undocumented steps.

---

## Phase Gate Reminder
No phase begins before the previous phase's Definition of Done is met **and** the next phase is explicitly requested. Any deviation — a new command, an architectural change, expanded scope — is raised as a question before implementation, per the project's Important Rules.
