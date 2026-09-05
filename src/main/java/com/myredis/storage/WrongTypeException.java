package com.myredis.storage;

public final class WrongTypeException extends RuntimeException {
    public WrongTypeException(String key, RedisType expected, RedisType actual) {
        super("WRONGTYPE key '" + key + "' contains " + actual + ", expected " + expected);
    }
}
