# MyRedis — Design Document

> Companion docs: [PRD.md](./PRD.md) · [Architecture.md](./Architecture.md) · [Phases.md](./Phases.md)

## 1. Purpose
Captures class-level design, key interfaces, and the rationale behind implementation choices, so each phase can be implemented without re-deriving decisions already made. This is a living document — revise the relevant section when a phase changes a decision, and log it in Architecture.md §14.

## 2. Design Principles Applied
SOLID (especially Single Responsibility and Dependency Inversion), composition over inheritance, small focused interfaces, constructor-based dependency wiring (no framework DI container), fail fast with explicit exceptions, no premature abstraction — concrete classes until a second real implementation is needed.

## 3. `server` package
- **`MyRedisServer`** — owns the `ServerSocket`, the accept loop, and lifecycle (`start()`/`stop()`). Delegates each accepted `Socket` to a `ClientHandler`.
- **`ClientHandler implements Runnable`** — owns one client's input/output streams; loop: decode → dispatch → encode → write, until the client disconnects or the server shuts down.
- **`ConnectionRegistry`** — tracks active `ClientHandler`s (`ConcurrentHashMap.newKeySet()`) so shutdown can close them gracefully.

*Rationale:* one class = one connection's lifecycle keeps `MyRedisServer` itself tiny and testable without real sockets (a fake acceptor is enough).

## 4. `protocol` package
- **`RespType`** enum — `SIMPLE_STRING, ERROR, INTEGER, BULK_STRING, ARRAY, NULL`.
- **`RespDecoder`** — reads from a buffered `InputStream`, parses one RESP value (recursively for arrays) into a Java representation (`List<String>` for a command). Also supports the simpler whitespace form used before Phase 8, so command parsing never has to change shape mid-project (see §9).
- **`RespEncoder`** — pure methods, `Object → byte[]`, one per RESP type plus a generic `encode(CommandResult)`.
- **`ProtocolException extends RuntimeException`** — malformed input.

*Rationale:* the decoder/encoder are the *only* place that knows about wire format; nothing above this package ever sees raw bytes.

## 5. `command` package
- **`Command`** (interface) — `CommandResult execute(CommandContext ctx)`.
- **`CommandContext`** — carries the `StorageEngine`, `ExpirationManager`, `PersistenceManager` references plus the raw arguments; passed to every command, keeping commands stateless and independently testable.
- **`CommandResult`** — small value object wrapping either a typed success value or an error message.
- **`CommandParser`** — decoded input (`List<String>`) → concrete `Command` instance via a `CommandRegistry`.
- **`CommandRegistry`** — `Map<String, CommandFactory>` populated at startup; each phase adds new commands only by registering new entries, never by touching parser control flow.
- One class per command (`PingCommand`, `SetCommand`, `GetCommand`, `DelCommand`, `ExistsCommand`, later `ExpireCommand`, list/set/hash/zset commands, …) — each small, single-purpose, unit-testable in isolation with a fake `StorageEngine`.

*Rationale:* this is the Command design pattern — it's what makes "add a command" mean "add one class + one registry line," matching the incremental phase philosophy.

## 6. `storage` package
- **`StorageEngine`** (interface) — type-specific methods (`Optional<String> getString(key)`, `void setString(key, value)`, `boolean delete(key)`, `boolean exists(key)`, plus later list/set/hash/zset methods) — **not** a generic `get(Object)`/`put(Object,Object)`, so each Redis type keeps its own correct semantics and the interface documents exactly what's supported.
- **`InMemoryStorageEngine implements StorageEngine`** — backed by a single `ConcurrentHashMap<String, RedisObject>`.
- **`RedisObject`** — `{ RedisType type; Object value; }`, a small tagged union; `value`'s runtime type is dictated by `type`.
- **`RedisType`** enum — `STRING, LIST, SET, HASH, ZSET`.
- **`WrongTypeException`** — thrown when a command targets a key whose stored type doesn't match (mirrors Redis's `WRONGTYPE` error).

*Rationale:* a typed interface (vs. `Object` everywhere) pushes type errors to compile time or to one exception type, instead of scattered `ClassCastException`s.

## 7. `expiration` package
- **`ExpirationManager`** — `ConcurrentHashMap<String, Long> expiryTimestamps` (epoch millis); `setExpiry`, `getTtl`, `persist(key)`, `isExpired(key)`.
- **Lazy expiration** — `StorageEngine` consults `ExpirationManager.isExpired(key)` before returning a value; if expired, deletes the key and treats it as absent.
- **Active expiration** — `ExpirationScheduler` (a `ScheduledExecutorService` task) periodically samples a batch of keys with a TTL and evicts expired ones, loosely modeled on Redis's own probabilistic cycle but intentionally simplified.

*Rationale:* splitting lazy (correctness) from active (memory reclamation) expiration mirrors why Redis does both, and is a good README teaching point.

## 8. `persistence` package
- **`AofWriter`** — appends each mutating command (re-serialized in a stable text/RESP form) to an append-only file; configurable fsync policy (`ALWAYS`, `EVERY_SECOND`, `NEVER`) via a background flush thread for `EVERY_SECOND`.
- **`AofReplayer`** — reads the AOF file at startup and re-executes each command through the **same** `CommandParser`/dispatcher path used for live traffic.
- **`SnapshotWriter` / `SnapshotLoader`** — serialize/deserialize the full `RedisObject` map (+ expiry map) to/from one versioned file.
- **`PersistenceManager`** — facade the rest of the system talks to; decides AOF vs. snapshot vs. both based on `ServerConfig`.

*Rationale:* replaying AOF through the *real* command path guarantees replay semantics never drift from live semantics.

## 9. `config` package
- **`ServerConfig`** (immutable) — host, port, thread-model settings, AOF enabled + path + fsync policy, snapshot interval + path, log level.
- **`ConfigLoader`** — defaults → properties file → CLI/env overrides, produces one `ServerConfig` at startup.

## 10. Concurrency Design
- **Default recommendation:** Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for one virtual thread per client connection — gives thread-per-connection simplicity without traditional platform-thread cost, and stays within the "Java standard library / `ExecutorService`" constraint. This is the *recommended* Phase 4 choice; per the project's own rule, it will be raised explicitly before implementation since the original spec also allows NIO.
- **Storage-level thread safety:** `ConcurrentHashMap` protects top-level key visibility/atomicity for single-key, single-op commands (`GET`, `SET`, `DEL`, `EXISTS`). Compound read-modify-write operations on collection types (`LPUSH`, `SADD`, `HSET`, `ZADD`, etc.) additionally synchronize on the retrieved `RedisObject` instance (intrinsic lock), keeping the critical section as small as one key rather than the whole map.
- **Expiration races** are handled by making "check expiry → evict → read" effectively atomic per key, via the same per-object lock used for compound ops.

## 11. Error Handling Design
Every command returns a `CommandResult` — it never throws to the client-facing layer. Internal exceptions (`WrongTypeException`, `ProtocolException`) are caught at the `ClientHandler` boundary and translated into a protocol-level error reply, so one bad command never kills a connection or the server.

## 12. Testing Design
- Unit tests mirror the package structure 1:1 under `src/test/java`.
- Each command class is tested with a fake or real in-memory `StorageEngine`, asserting `CommandResult` contents — not wire bytes.
- Integration tests own a real `MyRedisServer` bound to an ephemeral port, talk to it over a real `Socket`, and assert decoded responses.
- Concurrency tests spin up N threads against one shared key and assert an invariant (e.g. N `LPUSH`s → list length == N).
- Persistence tests write via commands, restart against a fresh `InMemoryStorageEngine` + replay AOF/snapshot, and assert state equality.

## 13. Design Alternatives Considered (deferred)
| Alternative | Why deferred |
|---|---|
| NIO/`Selector` single-reactor networking | More scalable at very high connection counts, but adds real complexity for little teaching value at this project's scale |
| Exact Redis LRU/active-expiration sampling algorithm | Simplified periodic sampler is sufficient; can be revisited in Phase 10 polish if desired |
| RESP3 support | Out of scope for v1 — see PRD Non-Goals |
