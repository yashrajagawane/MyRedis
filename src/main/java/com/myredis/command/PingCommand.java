package com.myredis.command;

public final class PingCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (!context.arguments().isEmpty() && context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'ping' command");
        }
        return new CommandResult(context.arguments().isEmpty()
                ? "PONG"
                : context.arguments().getFirst());
    }
}
