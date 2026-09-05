package com.myredis.command;

public final class ZscoreCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2) {
            return CommandResult.error("wrong number of arguments for 'zscore' command");
        }
        return new CommandResult(context.storage().sortedSetScore(
                context.arguments().getFirst(), context.arguments().get(1))
                .map(Object::toString).orElse("(nil)"));
    }
}
