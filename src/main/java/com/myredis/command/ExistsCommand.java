package com.myredis.command;

public final class ExistsCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().isEmpty()) {
            return CommandResult.error("wrong number of arguments for 'exists' command");
        }
        return new CommandResult("0");
    }
}
