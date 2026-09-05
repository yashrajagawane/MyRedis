package com.myredis.command;

public final class LpopCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'lpop' command");
        }
        return new CommandResult(context.storage().popLeft(context.arguments().getFirst()).orElse("(nil)"));
    }
}
