package com.myredis.command;

public final class GetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'get' command");
        }
        var value = context.storage().getString(context.arguments().getFirst());
        if (value.isPresent()) {
            context.metrics().recordGetHit();
            return new CommandResult(value.get());
        }
        context.metrics().recordGetMiss();
        return new CommandResult("(nil)");
    }
}
