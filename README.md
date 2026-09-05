# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 2 is implemented: a blocking TCP server listens on port `6379`, accepts clients on virtual threads, parses line-based commands, validates arguments, and shuts down gracefully. Storage is intentionally stubbed until Phase 3.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `PING`; the server replies with `PONG`. `SET` returns `OK`, while `GET`, `DEL`, and `EXISTS` return Phase 2 stub responses because storage is added in Phase 3.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
