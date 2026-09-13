package com.myredis.command;

/** Requests a clean shutdown of the current client connection. */
public final class QuitCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            return CommandResult.error("wrong number of arguments for 'quit' command");
        }
        return CommandResult.ok();
    }
}
