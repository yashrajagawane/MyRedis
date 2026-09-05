package com.myredis.command;

public final class ScardCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'scard' command");
        }
        return new CommandResult(Integer.toString(context.storage().setCardinality(
                context.arguments().getFirst())));
    }
}
