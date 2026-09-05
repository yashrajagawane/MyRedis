package com.myredis.command;

public final class TtlCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'ttl' command");
        }
        String key = context.arguments().getFirst();
        if (!context.storage().exists(key)) return new CommandResult("-2");
        return new CommandResult(Long.toString(context.expiration().ttlSeconds(key).orElse(-2)));
    }
}
