package com.myredis.protocol;

import com.myredis.command.CommandResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Encodes Phase 8 command results as RESP2 values. */
public final class RespEncoder {
    private static final Set<String> INTEGER_COMMANDS = Set.of(
            "DEL", "EXISTS", "EXPIRE", "PEXPIRE", "TTL", "PERSIST", "INCR", "DECR", "INCRBY", "LPUSH", "RPUSH", "LLEN",
            "SADD", "SREM", "SISMEMBER", "SCARD", "HSET", "HDEL", "HEXISTS", "ZADD", "ZREM", "ZRANK");
    private static final Set<String> ARRAY_COMMANDS = Set.of("LRANGE", "SMEMBERS", "HGETALL", "ZRANGE");

    public byte[] encode(CommandResult result, String commandName) {
        return encode(result, commandName, java.util.List.of());
    }

    public byte[] encode(CommandResult result, String commandName, List<String> arguments) {
        String response = result.response();
        if (response.startsWith("-ERR")) return (response + "\r\n").getBytes(StandardCharsets.UTF_8);
        if ("(nil)".equals(response)) return "$-1\r\n".getBytes(StandardCharsets.US_ASCII);
        if (result.arrayValues() != null) return encodeArray(result.arrayValues());
        if (ARRAY_COMMANDS.contains(commandName)) return encodeArray(response);
        if (INTEGER_COMMANDS.contains(commandName)) return (":" + response + "\r\n").getBytes(StandardCharsets.US_ASCII);
        if ("PING".equals(commandName) && arguments.isEmpty()) {
            return ("+" + response + "\r\n").getBytes(StandardCharsets.UTF_8);
        }
        if ("SET".equals(commandName)) return ("+" + response + "\r\n").getBytes(StandardCharsets.UTF_8);
        return encodeBulk(response);
    }

    private byte[] encodeBulk(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return ("$" + bytes.length + "\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encodeArray(List<String> values) {
        StringBuilder result = new StringBuilder("*").append(values.size()).append("\r\n");
        for (String item : values) {
            byte[] bytes = item.getBytes(StandardCharsets.UTF_8);
            result.append('$').append(bytes.length).append("\r\n").append(item).append("\r\n");
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encodeArray(String value) {
        if ("(nil)".equals(value)) return "*0\r\n".getBytes(StandardCharsets.US_ASCII);
        return encodeArray(java.util.Arrays.asList(value.split(" ")));
    }
}
