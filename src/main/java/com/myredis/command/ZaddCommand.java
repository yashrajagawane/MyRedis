package com.myredis.command;

public final class ZaddCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() < 3 || context.arguments().size() % 2 == 0) {
            return CommandResult.error("wrong number of arguments for 'zadd' command");
        }
        try {
            return new CommandResult(Integer.toString(context.storage().addSortedSetMembers(
                    context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
        } catch (IllegalArgumentException exception) {
            return CommandResult.error(exception.getMessage());
        }
    }
}
