# MyRedis

MyRedis is a learning-first Redis-inspired in-memory database built from scratch in Java 21.

## Current status

Phase 1 is implemented: a blocking TCP server listens on port `6379`, accepts clients on virtual threads, acknowledges line-based input, and shuts down gracefully. Command parsing and database storage are intentionally reserved for later phases.

## Run

```bash
mvn test
mvn package
java -jar target/myredis-0.1.0-SNAPSHOT.jar
```

Connect with `nc localhost 6379` and send a line such as `hello`; the server replies with `OK hello`.

See [PRD.md](PRD.md), [Architecture.md](Architecture.md), [Design.md](Design.md), and [Phases.md](Phases.md) for the project requirements and roadmap.
