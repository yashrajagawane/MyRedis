package com.myredis.server;

import com.myredis.command.CommandParseException;
import com.myredis.command.CommandParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles one Phase 1 client connection with a temporary acknowledgement protocol. */
public final class ClientHandler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final ConnectionRegistry connectionRegistry;
    private final CommandParser commandParser;

    public ClientHandler(Socket socket, ConnectionRegistry connectionRegistry, CommandParser commandParser) {
        this.socket = socket;
        this.connectionRegistry = connectionRegistry;
        this.commandParser = commandParser;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String input;
            while ((input = reader.readLine()) != null) {
                String response;
                try {
                    response = commandParser.parse(input).execute().response();
                } catch (CommandParseException exception) {
                    response = "-ERR " + exception.getMessage();
                }
                writer.write(response);
                writer.write("\r\n");
                writer.flush();
            }
        } catch (IOException exception) {
            LOGGER.debug("Client connection closed with an I/O error", exception);
        } finally {
            connectionRegistry.unregister(socket);
            LOGGER.info("Client disconnected");
        }
    }
}
