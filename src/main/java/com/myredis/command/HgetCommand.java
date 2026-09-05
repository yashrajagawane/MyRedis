package com.myredis.command;

public final class HgetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'hget' command");
        }
        return new CommandResult(context.storage().getHashField(
                context.arguments().getFirst(), context.arguments().get(1)).orElse("(nil)"));
    }
}
