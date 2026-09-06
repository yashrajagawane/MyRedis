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
    private final RespEncoder respEncoder = new RespEncoder();

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser) {
        this.socket = socket;
        this.connectionRegistry = connectionRegistry;
        this.commandParser = commandParser;
    }

    @Override
    public void run() {
        try (socket; InputStream input = socket.getInputStream(); OutputStream output = socket.getOutputStream()) {
            RespDecoder decoder = new RespDecoder(input);
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
                    output.write(("-ERR " + exception.getMessage() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.flush();
                }
            }
        } catch (IOException exception) {
            LOGGER.debug("Client connection closed with an I/O error", exception);
        } finally {
            connectionRegistry.unregister(socket);
            LOGGER.info("Client disconnected");
        }
    }
}
