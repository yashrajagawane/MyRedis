package com.myredis.command;

public final class HdelCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() < 2) {
            return CommandResult.error("wrong number of arguments for 'hdel' command");
        }
        return new CommandResult(Integer.toString(context.storage().removeHashFields(
                context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
    }
}
