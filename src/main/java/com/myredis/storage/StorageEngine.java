package com.myredis.storage;

import java.util.Optional;

public interface StorageEngine {
    void setString(String key, String value);

    Optional<String> getString(String key);

    boolean delete(String key);

    boolean exists(String key);
}
