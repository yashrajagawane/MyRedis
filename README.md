# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 3 is implemented: a blocking TCP server listens on port `6379`, accepts clients on virtual threads, parses line-based commands, and stores String values in an in-memory engine. Concurrency and additional Redis data types are reserved for later phases.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `SET key value`, followed by `GET key`; the server returns `OK` and then `value`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
