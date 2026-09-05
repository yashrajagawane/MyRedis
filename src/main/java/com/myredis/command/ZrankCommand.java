package com.myredis.command;

public final class ZrankCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'zrank' command");
        }
        return new CommandResult(context.storage().sortedSetRank(
                context.arguments().getFirst(), context.arguments().get(1))
                .map(Object::toString).orElse("(nil)"));
    }
}
