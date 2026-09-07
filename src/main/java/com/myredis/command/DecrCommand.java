package com.myredis.command;

public final class DecrCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'decr' command");
        }
        return IncrCommand.increment(context, -1);
    }
}
