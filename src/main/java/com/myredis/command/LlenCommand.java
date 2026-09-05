package com.myredis.command;

public final class LlenCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'llen' command");
        }
        return new CommandResult(Integer.toString(context.storage().listLength(
                context.arguments().getFirst())));
    }
}
