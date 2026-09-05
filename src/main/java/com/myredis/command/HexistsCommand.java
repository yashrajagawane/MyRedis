package com.myredis.command;

public final class HexistsCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'hexists' command");
        }
        return new CommandResult(context.storage().hasHashField(
                context.arguments().getFirst(), context.arguments().get(1)) ? "1" : "0");
    }
}
