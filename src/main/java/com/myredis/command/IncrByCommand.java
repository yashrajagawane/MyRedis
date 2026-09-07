package com.myredis.command;

public final class IncrByCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'incrby' command");
        }
        try {
            return IncrCommand.increment(context, Long.parseLong(context.arguments().get(1)));
        } catch (NumberFormatException exception) {
            return CommandResult.error("value is not an integer or out of range");
        }
    }
}
