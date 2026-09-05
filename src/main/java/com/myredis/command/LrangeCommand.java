package com.myredis.command;

import java.util.List;

public final class LrangeCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 3) {
            return CommandResult.error("wrong number of arguments for 'lrange' command");
        }
        Integer start = ListCommandSupport.parseIndex(context.arguments().get(1), "lrange");
        Integer stop = ListCommandSupport.parseIndex(context.arguments().get(2), "lrange");
        if (start == null || stop == null) {
            return CommandResult.error("value is not an integer or out of range");
        }
        List<String> values = context.storage().range(context.arguments().getFirst(), start, stop);
        return new CommandResult(values.isEmpty() ? "(nil)" : String.join(" ", values));
    }
}
