package com.myredis.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class PersistenceCodec {
    private PersistenceCodec() {
    }

    static String encode(List<String> arguments) {
        return arguments.stream()
                .map(value -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    static List<String> decode(String line) {
        return java.util.Arrays.stream(line.trim().split("\\s+"))
                .map(value -> new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                .toList();
    }
}
