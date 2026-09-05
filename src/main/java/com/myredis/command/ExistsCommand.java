package com.myredis.command;

public final class ExistsCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().isEmpty()) {
            return CommandResult.error("wrong number of arguments for 'exists' command");
        }
        long existing = context.arguments().stream()
                .filter(context.storage()::exists)
                .count();
        return new CommandResult(Long.toString(existing));
    }
}
