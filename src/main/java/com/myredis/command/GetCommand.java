package com.myredis.command;

public final class GetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'get' command");
        }
        return new CommandResult("(nil)");
    }
}
