package com.myredis.command;

public final class IncrCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 1) {
            return CommandResult.error("wrong number of arguments for 'incr' command");
        }
        return increment(context, 1);
    }

    static CommandResult increment(CommandContext context, long delta) {
        try {
            return new CommandResult(Long.toString(context.storage()
                    .incrementString(context.arguments().getFirst(), delta)));
        } catch (IllegalArgumentException exception) {
            return CommandResult.error(exception.getMessage());
        }
    }
}
