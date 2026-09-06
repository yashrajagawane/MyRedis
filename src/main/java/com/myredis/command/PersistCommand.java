package com.myredis.command;

public final class PersistCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'persist' command");
        }
        if (!context.storage().exists(context.arguments().getFirst())) {
            return new CommandResult("0");
        }
        return new CommandResult(context.expiration().persist(context.arguments().getFirst()) ? "1" : "0");
    }
}
