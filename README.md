# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 5c is implemented: a blocking TCP server accepts concurrent clients through Java 21 virtual threads, tracks active connections for graceful shutdown, and stores String, List, Set, and Hash values in a thread-safe in-memory engine. Sorted Sets are reserved for the next sub-phase.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `SET key value`, followed by `GET key`; the server returns `OK` and then `value`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
