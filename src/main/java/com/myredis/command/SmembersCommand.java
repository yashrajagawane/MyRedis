package com.myredis.command;

public final class SmembersCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'smembers' command");
        }
        var members = context.storage().setMembers(context.arguments().getFirst());
        return CommandResult.array(members);
    }
}
