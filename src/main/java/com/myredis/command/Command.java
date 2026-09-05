package com.myredis.command;

/** A stateless executable command. */
@FunctionalInterface
public interface Command {
    CommandResult execute(CommandContext context);
}
