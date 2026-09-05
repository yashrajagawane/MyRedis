package com.myredis.command;

public final class HgetallCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'hgetall' command");
        }
        var fields = context.storage().getAllHashFields(context.arguments().getFirst());
        return new CommandResult(fields.isEmpty() ? "(nil)" : String.join(" ", fields));
    }
}
