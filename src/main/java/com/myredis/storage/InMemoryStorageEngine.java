package com.myredis.storage;

import java.util.Map;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe String storage for concurrent Phase 4 clients. */
public final class InMemoryStorageEngine implements StorageEngine {
    private final Map<String, RedisObject> values = new ConcurrentHashMap<>();

    @Override
    public void setString(String key, String value) {
        requireKey(key);
        values.put(key, new RedisObject(RedisType.STRING, value));
    }

    @Override
    public Optional<String> getString(String key) {
        RedisObject object = values.get(key);
        if (object == null) {
            return Optional.empty();
        }
        requireType(key, object, RedisType.STRING);
        return Optional.of((String) object.value());
    }

    @Override
    public boolean delete(String key) {
        return values.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        return values.containsKey(key);
    }

    @Override
    public int pushLeft(String key, List<String> elements) {
        requireKey(key);
        RedisObject object = values.computeIfAbsent(key,
                ignored -> new RedisObject(RedisType.LIST, new ArrayDeque<String>()));
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            Deque<String> list = listValue(object);
            elements.reversed().forEach(list::addFirst);
            return list.size();
        }
    }

    @Override
    public int pushRight(String key, List<String> elements) {
        requireKey(key);
        RedisObject object = values.computeIfAbsent(key,
                ignored -> new RedisObject(RedisType.LIST, new ArrayDeque<String>()));
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            Deque<String> list = listValue(object);
            elements.forEach(list::addLast);
            return list.size();
        }
    }

    @Override
    public Optional<String> popLeft(String key) {
        return pop(key, true);
    }

    @Override
    public Optional<String> popRight(String key) {
        return pop(key, false);
    }

    @Override
    public List<String> range(String key, int start, int stop) {
        RedisObject object = values.get(key);
        if (object == null) {
            return List.of();
        }
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            List<String> items = new ArrayList<>(listValue(object));
            int from = normalizeIndex(start, items.size());
            int to = normalizeIndex(stop, items.size());
            if (from > to || from >= items.size() || to < 0) {
                return List.of();
            }
            from = Math.max(from, 0);
            to = Math.min(to, items.size() - 1);
            return List.copyOf(items.subList(from, to + 1));
        }
    }

    @Override
    public int listLength(String key) {
        RedisObject object = values.get(key);
        if (object == null) {
            return 0;
        }
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            return listValue(object).size();
        }
    }

    private Optional<String> pop(String key, boolean left) {
        RedisObject object = values.get(key);
        if (object == null) {
            return Optional.empty();
        }
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            Deque<String> list = listValue(object);
            Optional<String> result = Optional.ofNullable(left ? list.pollFirst() : list.pollLast());
            if (list.isEmpty()) {
                values.remove(key, object);
            }
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private static Deque<String> listValue(RedisObject object) {
        return (Deque<String>) object.value();
    }

    private static int normalizeIndex(int index, int size) {
        return index < 0 ? size + index : index;
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
    }

    private static void requireType(String key, RedisObject object, RedisType expected) {
        if (object.type() != expected) {
            throw new WrongTypeException(key, expected, object.type());
        }
    }
}
