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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
        this.socket = socket;
        this.connectionRegistry = connectionRegistry;
        this.commandParser = commandParser;
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
        this.metrics = metrics;
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

    private void writeResponse(CommandResult result, String name, List<String> arguments,
                               boolean resp, OutputStream output) throws IOException {
        if (resp) output.write(respEncoder.encode(result, name, arguments));
        else output.write((result.response() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
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
