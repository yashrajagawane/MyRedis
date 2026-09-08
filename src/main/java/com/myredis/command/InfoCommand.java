package com.myredis.command;

public final class InfoCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            return CommandResult.error("wrong number of arguments for 'info' command");
        }
        return new CommandResult(context.metrics().info(context.expiration().expiredKeys()));
    }
}
