# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 7 is implemented: MyRedis logs mutating commands to an AOF, writes versioned snapshots, and restores snapshots plus the AOF tail on startup. The default application data files are `data/myredis.aof` and `data/myredis.snapshot`.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `SET key value`, followed by `GET key`; the server returns `OK` and then `value`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
