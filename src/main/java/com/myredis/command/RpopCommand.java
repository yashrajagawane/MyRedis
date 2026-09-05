package com.myredis.command;

public final class RpopCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'rpop' command");
        }
        return new CommandResult(context.storage().popRight(context.arguments().getFirst()).orElse("(nil)"));
    }
}
