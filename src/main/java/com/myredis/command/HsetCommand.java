package com.myredis.command;

public final class HsetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() < 3 || context.arguments().size() % 2 == 0) {
            return CommandResult.error("wrong number of arguments for 'hset' command");
        }
        return new CommandResult(Integer.toString(context.storage().putHashFields(
                context.arguments().getFirst(), context.arguments().subList(1, context.arguments().size()))));
    }
}
