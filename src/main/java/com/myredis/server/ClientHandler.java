package com.myredis.server;

import com.myredis.command.CommandParseException;
import com.myredis.command.CommandParser;
import com.myredis.protocol.ProtocolException;
import com.myredis.protocol.RespDecoder;
import com.myredis.protocol.RespEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
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
    private final RespEncoder respEncoder = new RespEncoder();

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser) {
        this(socket, connectionRegistry, commandParser, RespDecoder.DEFAULT_MAX_VALUE_BYTES,
                RespDecoder.DEFAULT_MAX_ARRAY_ELEMENTS);
    }

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser,
                         int maxValueBytes, int maxArrayElements) {
        this.socket = socket;
        this.connectionRegistry = connectionRegistry;
        this.commandParser = commandParser;
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
    }

    @Override
    public void run() {
        try (socket; InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {
            RespDecoder decoder = new RespDecoder(input, maxValueBytes, maxArrayElements);
            int firstByte;
            while ((firstByte = decoder.readFirstByte()) >= 0) {
                try {
                    if (firstByte == '*') {
                        var parsed = commandParser.parse(decoder.readCommandAfterPrefix());
                        output.write(respEncoder.encode(parsed.execute(), parsed.name()));
                    } else {
                        String line = decoder.readPlainLine(firstByte);
                        var parsed = commandParser.parse(line);
                        output.write((parsed.execute().response() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    output.flush();
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
            LOGGER.info("Client disconnected");
        }
    }

    private static void writeError(OutputStream output, String message) throws IOException {
        String safeMessage = message == null || message.isBlank()
                ? "internal server error"
                : message.replace('\r', ' ').replace('\n', ' ');
        output.write(("-ERR " + safeMessage + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
    }
}
