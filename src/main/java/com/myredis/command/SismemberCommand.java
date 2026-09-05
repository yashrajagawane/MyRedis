package com.myredis.command;

public final class SismemberCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'sismember' command");
        }
        return new CommandResult(context.storage().isSetMember(
                context.arguments().getFirst(), context.arguments().get(1)) ? "1" : "0");
    }
}
