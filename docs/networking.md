# Networking

MyRedis uses a blocking `ServerSocket` accept loop with Java 21 virtual threads
for client handlers. This keeps the networking implementation small and makes
connection ownership explicit while allowing many mostly-idle clients.

## Accept path

`MyRedisServer` binds to the configured host and port, then accepts sockets until
shutdown. Each socket is checked against `limits.max.connections` before it is
registered and submitted to the virtual-thread executor. Connections over the
limit are closed immediately and do not receive a command handler.

## Client handler

`ClientHandler` owns one socket and its input/output streams. It repeatedly:

1. Reads the next request prefix.
2. Decodes a RESP2 command array or plain-text line.
3. Parses and executes the command.
4. Encodes and flushes one response.

The loop supports multiple commands and pipelines on one connection. Command
errors are returned to that client, while unexpected runtime failures are
logged and converted to an error response where possible. The handler closes
its resources and unregisters the socket in all exit paths.

## Protocol boundaries

The networking layer does not know storage semantics. It delegates wire parsing
to `RespDecoder`, command dispatch to `CommandParser`, and response formatting
to `RespEncoder` or the plain-text response path. Decoder limits protect the
handler from unbounded bulk values, command arrays, and protocol metadata.

## Shutdown

`stop()` closes the listening socket and all registered client sockets, stops
the expiration scheduler, and interrupts client handlers. The server waits up
to two seconds for handlers to terminate. The final server cleanup also closes
persistence. A JVM shutdown hook invokes the same server stop path.

## Failure isolation

Malformed requests and client I/O failures are handled inside the client
handler. One broken or disconnected client therefore does not terminate the
accept loop or other client handlers. Server-level accept failures are logged
while the server is still running.

## Current tradeoffs

The server does not use NIO selectors, TLS, authentication, request-rate
limiting, idle timeouts, or a bounded command executor. Virtual threads reduce
the cost of blocked handlers, but connection and payload limits remain
important operational controls. End-to-end network p95/p99 benchmarks are not
yet part of the automated benchmark suite.
