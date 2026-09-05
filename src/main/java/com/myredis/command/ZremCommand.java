package com.myredis.command;

public final class ZremCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        CommandResult error = ListCommandSupport.requireAtLeast(context.arguments(), 2, "zrem");
        if (error != null) return error;
        return new CommandResult(Integer.toString(context.storage().removeSortedSetMembers(
                context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
    }
}
