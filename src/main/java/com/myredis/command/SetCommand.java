package com.myredis.command;

public final class SetCommand implements Command {
    @Override
    public CommandResult execute(CommandContext context) {
        if (context.arguments().size() != 2 && context.arguments().size() != 4) {
            return CommandResult.error("wrong number of arguments for 'set' command");
        }
        long expiryMillis = 0;
        if (context.arguments().size() == 4) {
            String option = context.arguments().get(2).toUpperCase();
            long amount;
            try {
                amount = Long.parseLong(context.arguments().get(3));
                expiryMillis = option.equals("EX") ? Math.multiplyExact(amount, 1000) : amount;
            } catch (NumberFormatException | ArithmeticException exception) {
                return CommandResult.error("value is not an integer or out of range");
            }
            if (amount <= 0 || (!option.equals("EX") && !option.equals("PX"))) {
                return CommandResult.error("syntax error");
            }
        }
        context.storage().setString(context.arguments().get(0), context.arguments().get(1));
        if (expiryMillis > 0) {
            context.expiration().setExpiryMillis(context.arguments().getFirst(), expiryMillis);
        }
        return CommandResult.ok();
    }
}
