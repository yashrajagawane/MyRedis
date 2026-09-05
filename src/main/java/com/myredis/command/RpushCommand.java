package com.myredis.command;

public final class RpushCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        CommandResult error = ListCommandSupport.requireAtLeast(context.arguments(), 2, "rpush");
        if (error != null) return error;
        return new CommandResult(Integer.toString(context.storage().pushRight(
                context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
    }
}
