package com.myredis.server;

import com.myredis.command.CommandParseException;
import com.myredis.command.CommandParser;
import com.myredis.command.CommandResult;
import com.myredis.protocol.ProtocolException;
import com.myredis.protocol.RespDecoder;
import com.myredis.protocol.RespEncoder;
import com.myredis.observability.ServerMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles one Phase 1 client connection with a temporary acknowledgement protocol. */
public final class ClientHandler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ConnectionRegistry connectionRegistry;
    private final CommandParser commandParser;
    private final int maxValueBytes;
    private final int maxArrayElements;
    private final ServerMetrics metrics;
    private final RespEncoder respEncoder = new RespEncoder();
    private final PubSubBroker pubSubBroker;
    private final String authPassword;
    private final BlockingQueue<List<String>> pubSubMessages = new ArrayBlockingQueue<>(PubSubBroker.MAX_PENDING_MESSAGES);
    private final Set<String> subscribedChannels = new java.util.HashSet<>();
    private volatile boolean closed;
    private Thread pubSubWriter;
    private boolean authenticated;
    private final List<CommandParser.ParsedCommand> transactionQueue = new ArrayList<>();
    private boolean inTransaction;
    private boolean transactionFailed;

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser) {
        this(socket, connectionRegistry, commandParser, RespDecoder.DEFAULT_MAX_VALUE_BYTES,
                RespDecoder.DEFAULT_MAX_ARRAY_ELEMENTS, commandParser.metrics());
    }

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                         int maxValueBytes, int maxArrayElements) {
        this(socket, connectionRegistry, commandParser, maxValueBytes, maxArrayElements, commandParser.metrics());
    }

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                         int maxValueBytes, int maxArrayElements, ServerMetrics metrics) {
        this(socket, connectionRegistry, commandParser, maxValueBytes, maxArrayElements, metrics, new PubSubBroker());
    }

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                         int maxValueBytes, int maxArrayElements, PubSubBroker pubSubBroker) {
        this(socket, connectionRegistry, commandParser, maxValueBytes, maxArrayElements, pubSubBroker, "");
    }

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                         int maxValueBytes, int maxArrayElements, PubSubBroker pubSubBroker, String authPassword) {
        this(socket, connectionRegistry, commandParser, maxValueBytes, maxArrayElements, commandParser.metrics(),
                pubSubBroker, authPassword);
    }

    private ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                          int maxValueBytes, int maxArrayElements, ServerMetrics metrics, PubSubBroker pubSubBroker) {
        this(socket, connectionRegistry, commandParser, maxValueBytes, maxArrayElements, metrics, pubSubBroker, "");
    }

    private ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                          int maxValueBytes, int maxArrayElements, ServerMetrics metrics, PubSubBroker pubSubBroker,
                          String authPassword) {
        this.socket = socket;
        this.connectionRegistry = connectionRegistry;
        this.commandParser = commandParser;
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
        this.metrics = metrics;
        this.pubSubBroker = pubSubBroker;
        this.authPassword = authPassword == null ? "" : authPassword;
        this.authenticated = this.authPassword.isEmpty();
    }

    @Override
    public void run() {
        try (socket; InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {
            RespDecoder decoder = new RespDecoder(input, maxValueBytes, maxArrayElements);
            int firstByte;
            while ((firstByte = decoder.readFirstByte()) >= 0) {
                try {
                    if (firstByte == '*') {
                        if (handleCommand(decoder.readCommandAfterPrefix(), true, output)) break;
                    } else {
                        if (handleCommand(Arrays.asList(decoder.readPlainLine(firstByte).trim().split("\\s+")), false, output)) {
                            break;
                        }
                    }
                } catch (CommandParseException | ProtocolException exception) {
                    writeError(output, exception.getMessage());
                } catch (RuntimeException exception) {
                    LOGGER.warn("Command failed for {}", socket.getRemoteSocketAddress(), exception);
                    writeError(output, exception instanceof IllegalStateException
                            ? "internal server error" : exception.getMessage());
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("Client connection closed with an I/O error", exception);
        } finally {
            closed = true;
            pubSubBroker.unsubscribeAll(pubSubMessages);
            if (pubSubWriter != null) pubSubWriter.interrupt();
            connectionRegistry.unregister(socket);
            metrics.clientDisconnected();
            LOGGER.info("Client disconnected");
        }
    }

    private boolean handleCommand(List<String> tokens, boolean resp, OutputStream output) throws IOException {
        if (tokens.isEmpty() || tokens.getFirst().isBlank()) {
            writeResponse(CommandResult.error("empty command"), "", List.of(), resp, output);
            return false;
        }
        String name = tokens.getFirst().toUpperCase(Locale.ROOT);
        List<String> arguments = tokens.subList(1, tokens.size());
        if ("AUTH".equals(name)) {
            return handleAuth(arguments, resp, output);
        }
        if (!authenticated && !Set.of("PING", "QUIT").contains(name)) {
            writeError(output, "NOAUTH Authentication required");
            return false;
        }
        if (Set.of("SUBSCRIBE", "UNSUBSCRIBE", "PUBLISH").contains(name)) {
            return handlePubSub(name, arguments, resp, output);
        }
        if (!subscribedChannels.isEmpty() && !Set.of("PING", "QUIT").contains(name)) {
            writeError(output, "only (P)SUBSCRIBE, (P)UNSUBSCRIBE, PING, QUIT are allowed in this context");
            return false;
        }
        if ("MULTI".equals(name)) {
            if (!arguments.isEmpty()) {
                writeResponse(CommandResult.error("wrong number of arguments for 'multi' command"), name, arguments, resp, output);
            } else if (inTransaction) {
                writeResponse(CommandResult.error("MULTI calls can not be nested"), name, arguments, resp, output);
            } else {
                inTransaction = true;
                transactionFailed = false;
                transactionQueue.clear();
                writeResponse(new CommandResult("OK"), name, arguments, resp, output);
            }
            return false;
        }
        if ("DISCARD".equals(name)) {
            if (!arguments.isEmpty()) {
                writeResponse(CommandResult.error("wrong number of arguments for 'discard' command"), name, arguments, resp, output);
            } else if (!inTransaction) {
                writeResponse(CommandResult.error("DISCARD without MULTI"), name, arguments, resp, output);
            } else {
                clearTransaction();
                writeResponse(new CommandResult("OK"), name, arguments, resp, output);
            }
            return false;
        }
        if ("EXEC".equals(name)) {
            if (!arguments.isEmpty()) {
                writeResponse(CommandResult.error("wrong number of arguments for 'exec' command"), name, arguments, resp, output);
            } else if (!inTransaction) {
                writeResponse(CommandResult.error("EXEC without MULTI"), name, arguments, resp, output);
            } else if (transactionFailed) {
                clearTransaction();
                writeResponse(CommandResult.error("EXECABORT Transaction discarded because of previous errors."), name, arguments, resp, output);
            } else {
                List<CommandResult> results = transactionQueue.stream().map(CommandParser.ParsedCommand::execute).toList();
                List<String> commandNames = transactionQueue.stream().map(CommandParser.ParsedCommand::name).toList();
                clearTransaction();
                if (resp) {
                    output.write(respEncoder.encodeResults(results, commandNames));
                    output.flush();
                } else {
                    for (CommandResult result : results) {
                        output.write((result.response() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    output.flush();
                }
            }
            return false;
        }
        try {
            CommandParser.ParsedCommand parsed = commandParser.parse(tokens);
            if (inTransaction) {
                transactionQueue.add(parsed);
                writeResponse(new CommandResult("QUEUED"), name, arguments, resp, output);
                return false;
            }
            writeResponse(parsed.execute(), parsed.name(), parsed.arguments(), resp, output);
            return "QUIT".equals(parsed.name());
        } catch (CommandParseException exception) {
            if (inTransaction) transactionFailed = true;
            writeError(output, exception.getMessage());
            return false;
        }
    }

    private boolean handleAuth(List<String> arguments, boolean resp, OutputStream output) throws IOException {
        if (arguments.size() != 1) {
            writeError(output, "wrong number of arguments for 'auth' command");
            return false;
        }
        if (authPassword.isEmpty()) {
            writeResponse(new CommandResult("OK"), "AUTH", arguments, resp, output);
            return false;
        }
        boolean matches = MessageDigest.isEqual(authPassword.getBytes(StandardCharsets.UTF_8),
                arguments.getFirst().getBytes(StandardCharsets.UTF_8));
        if (matches) {
            authenticated = true;
            writeResponse(new CommandResult("OK"), "AUTH", arguments, resp, output);
        } else {
            writeError(output, "invalid username-password pair");
        }
        return false;
    }

    private void writeResponse(CommandResult result, String name, List<String> arguments,
                               boolean resp, OutputStream output) throws IOException {
        synchronized (output) {
            if (resp) output.write(respEncoder.encode(result, name, arguments));
            else output.write((result.response() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
        }
    }

    private boolean handlePubSub(String name, List<String> arguments, boolean resp, OutputStream output) throws IOException {
        if ("PUBLISH".equals(name)) {
            if (arguments.size() != 2) {
                writeError(output, "wrong number of arguments for 'publish' command");
            } else {
                writeResponse(new CommandResult(Integer.toString(pubSubBroker.publish(arguments.get(0), arguments.get(1)))),
                        name, arguments, resp, output);
            }
            return false;
        }
        if (arguments.isEmpty()) {
            writeError(output, "wrong number of arguments for '" + name.toLowerCase(Locale.ROOT) + "' command");
            return false;
        }
        startPubSubWriter(output);
        for (String channel : arguments) {
            int count;
            if ("SUBSCRIBE".equals(name)) {
                if (subscribedChannels.add(channel)) pubSubBroker.subscribe(channel, pubSubMessages);
                count = subscribedChannels.size();
            } else {
                subscribedChannels.remove(channel);
                count = pubSubBroker.unsubscribe(channel, pubSubMessages);
            }
            sendSubscriptionEvent(name.toLowerCase(Locale.ROOT), channel, count, resp, output);
        }
        return false;
    }

    private void startPubSubWriter(OutputStream output) {
        if (pubSubWriter != null) return;
        pubSubWriter = Thread.startVirtualThread(() -> {
            try {
                while (!closed) {
                    List<String> event = pubSubMessages.take();
                    synchronized (output) {
                        output.write(respEncoder.encodeArray(event));
                        output.flush();
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                LOGGER.debug("Pub/Sub client writer closed", exception);
            }
        });
    }

    private void sendSubscriptionEvent(String event, String channel, int count,
                                       boolean resp, OutputStream output) throws IOException {
        if (resp) {
            synchronized (output) {
                output.write(respEncoder.encodeArray(List.of(event, channel, Integer.toString(count))));
                output.flush();
            }
        } else {
            writeResponse(new CommandResult(event + " " + channel + " " + count), "", List.of(), false, output);
        }
    }

    private void clearTransaction() {
        inTransaction = false;
        transactionFailed = false;
        transactionQueue.clear();
    }

    private static void writeError(OutputStream output, String message) throws IOException {
        String safeMessage = message == null || message.isBlank()
                ? "internal server error"
                : message.replace('\r', ' ').replace('\n', ' ');
        output.write(("-ERR " + safeMessage + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
    }
}
