# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

RESP2 support is implemented, and the repository includes JMH storage benchmarks plus a 100-client concurrent load test. Persistence uses `data/myredis.aof` and `data/myredis.snapshot`.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send `SET key value`, followed by `GET key`; the server returns `OK` and then `value`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
