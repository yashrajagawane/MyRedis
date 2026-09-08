package com.myredis.command;

public final class PexpireCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'pexpire' command");
        }
        try {
            long millis = Long.parseLong(context.arguments().get(1));
            if (millis <= 0 || !context.storage().exists(context.arguments().getFirst())) {
                return new CommandResult("0");
            }
            context.expiration().setExpiryMillis(context.arguments().getFirst(), millis);
            return new CommandResult("1");
        } catch (IllegalArgumentException exception) {
            return CommandResult.error("value is not an integer or out of range");
        }
    }
}
