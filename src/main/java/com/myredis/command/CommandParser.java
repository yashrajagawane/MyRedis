package com.myredis.command;

import java.util.Arrays;
import java.util.List;

/** Converts the Phase 2 whitespace command format into an executable command. */
public final class CommandParser {
    private final CommandRegistry registry;

    public CommandParser(CommandRegistry registry) {
        this.registry = registry;
    }

    public ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            throw new CommandParseException("empty command");
        }

        List<String> tokens = Arrays.stream(input.trim().split("\\s+"))
                .toList();
        String name = tokens.getFirst().toUpperCase();
        Command command = registry.find(name)
                .orElseThrow(() -> new CommandParseException(
                        "unknown command '" + tokens.getFirst() + "'"));
        return new ParsedCommand(name, command, tokens.subList(1, tokens.size()));
    }

    public record ParsedCommand(String name, Command command, List<String> arguments) {
        public ParsedCommand {
            arguments = List.copyOf(arguments);
        }

        public CommandResult execute() {
            return command.execute(new CommandContext(arguments));
        }
    }
}
