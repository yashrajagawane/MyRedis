package com.myredis.command;

public final class ExpireCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'expire' command");
        }
        try {
            long seconds = Long.parseLong(context.arguments().get(1));
            if (seconds <= 0 || !context.storage().exists(context.arguments().getFirst())) {
                return new CommandResult("0");
            }
            context.expiration().setExpiryMillis(context.arguments().getFirst(), seconds * 1000);
            return new CommandResult("1");
        } catch (NumberFormatException exception) {
            return CommandResult.error("value is not an integer or out of range");
        }
    }
}
