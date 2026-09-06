package com.myredis.command;

public final class ZrangeCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 3) {
            return CommandResult.error("wrong number of arguments for 'zrange' command");
        }
        Integer start = parseIndex(context.arguments().get(1));
        Integer stop = parseIndex(context.arguments().get(2));
        if (start == null || stop == null) return CommandResult.error("value is not an integer or out of range");
        var members = context.storage().sortedSetRange(context.arguments().getFirst(), start, stop);
        return CommandResult.array(members);
    }

    private static Integer parseIndex(String value) {
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { return null; }
    }
}
