package com.myredis.command;

public final class SaddCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        CommandResult error = ListCommandSupport.requireAtLeast(context.arguments(), 2, "sadd");
        if (error != null) return error;
        return new CommandResult(Integer.toString(context.storage().addSet(
                context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
    }
}
