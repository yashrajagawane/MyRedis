package com.myredis.command;

import java.util.List;

final class ListCommandSupport {
    private ListCommandSupport() {
    }

    static CommandResult requireAtLeast(List<String> arguments, int count, String name) {
        return arguments.size() < count
                ? CommandResult.error("wrong number of arguments for '" + name + "' command")
                : null;
    }

    static Integer parseIndex(String value, String command) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
