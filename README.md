# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 6 is implemented: a blocking TCP server accepts concurrent clients through Java 21 virtual threads, stores all v1 data types in a thread-safe in-memory engine, and supports TTL through `EXPIRE`, `TTL`, `PERSIST`, and `SET ... EX/PX` with lazy and active expiration.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `SET key value`, followed by `GET key`; the server returns `OK` and then `value`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
