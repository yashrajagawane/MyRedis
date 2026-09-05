package com.myredis.storage;

import java.util.Optional;
import java.util.List;

public interface StorageEngine {
    void setString(String key, String value);

    Optional<String> getString(String key);

    boolean delete(String key);

    boolean exists(String key);

    int pushLeft(String key, List<String> values);

    int pushRight(String key, List<String> values);

    Optional<String> popLeft(String key);

    Optional<String> popRight(String key);

    List<String> range(String key, int start, int stop);

    int listLength(String key);
}
