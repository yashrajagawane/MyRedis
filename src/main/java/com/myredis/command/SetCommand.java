package com.myredis.command;

public final class SetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'set' command");
        }
        context.storage().setString(context.arguments().get(0), context.arguments().get(1));
        return CommandResult.ok();
    }
}
