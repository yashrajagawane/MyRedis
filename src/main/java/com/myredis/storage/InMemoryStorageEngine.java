package com.myredis.storage;

import com.myredis.expiration.ExpirationManager;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe String storage for concurrent Phase 4 clients. */
public final class InMemoryStorageEngine implements StorageEngine {
    private final Map<String, RedisObject> values = new ConcurrentHashMap<>();
    private final ExpirationManager expiration;

    public InMemoryStorageEngine() {
        this(new ExpirationManager());
    }

    public InMemoryStorageEngine(ExpirationManager expiration) {
        this.expiration = expiration;
    }

    public ExpirationManager expirationManager() {
        return expiration;
    }

    @Override
    public void setString(String key, String value) {
        requireKey(key);
        values.put(key, new RedisObject(RedisType.STRING, value));
        expiration.removeExpiry(key);
    }

    @Override
    public Optional<String> getString(String key) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return Optional.empty();
        }
        requireType(key, object, RedisType.STRING);
        return Optional.of((String) object.value());
    }

    @Override
    public boolean delete(String key) {
        boolean removed = values.remove(key) != null;
        if (removed) expiration.removeExpiry(key);
        return removed;
    }

    @Override
    public boolean exists(String key) {
        removeIfExpired(key);
        return values.containsKey(key);
    }

    @Override
    public int pushLeft(String key, List<String> elements) {
        requireKey(key);
        removeIfExpired(key);
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
        removeIfExpired(key);
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
        removeIfExpired(key);
        return pop(key, true);
    }

    @Override
    public Optional<String> popRight(String key) {
        removeIfExpired(key);
        return pop(key, false);
    }

    @Override
    public List<String> range(String key, int start, int stop) {
        removeIfExpired(key);
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
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return 0;
        }
        requireType(key, object, RedisType.LIST);
        synchronized (object) {
            return listValue(object).size();
        }
    }

    @Override
    public int addSet(String key, List<String> members) {
        requireKey(key);
        removeIfExpired(key);
        RedisObject object = values.computeIfAbsent(key,
                ignored -> new RedisObject(RedisType.SET, new HashSet<String>()));
        requireType(key, object, RedisType.SET);
        synchronized (object) {
            Set<String> set = setValue(object);
            int added = 0;
            for (String member : members) {
                if (set.add(member)) {
                    added++;
                }
            }
            return added;
        }
    }

    @Override
    public int removeSet(String key, List<String> members) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return 0;
        }
        requireType(key, object, RedisType.SET);
        synchronized (object) {
            Set<String> set = setValue(object);
            int removed = 0;
            for (String member : members) {
                if (set.remove(member)) {
                    removed++;
                }
            }
            if (set.isEmpty()) {
                values.remove(key, object);
            }
            return removed;
        }
    }

    @Override
    public List<String> setMembers(String key) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return List.of();
        }
        requireType(key, object, RedisType.SET);
        synchronized (object) {
            return setValue(object).stream().sorted().toList();
        }
    }

    @Override
    public boolean isSetMember(String key, String member) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return false;
        }
        requireType(key, object, RedisType.SET);
        synchronized (object) {
            return setValue(object).contains(member);
        }
    }

    @Override
    public int setCardinality(String key) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) {
            return 0;
        }
        requireType(key, object, RedisType.SET);
        synchronized (object) {
            return setValue(object).size();
        }
    }

    @Override
    public int putHashFields(String key, List<String> fieldValues) {
        requireKey(key);
        removeIfExpired(key);
        RedisObject object = values.computeIfAbsent(key,
                ignored -> new RedisObject(RedisType.HASH, new HashMap<String, String>()));
        requireType(key, object, RedisType.HASH);
        synchronized (object) {
            Map<String, String> hash = hashValue(object);
            int added = 0;
            for (int index = 0; index < fieldValues.size(); index += 2) {
                if (!hash.containsKey(fieldValues.get(index))) {
                    added++;
                }
                hash.put(fieldValues.get(index), fieldValues.get(index + 1));
            }
            return added;
        }
    }

    @Override
    public Optional<String> getHashField(String key, String field) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return Optional.empty();
        requireType(key, object, RedisType.HASH);
        synchronized (object) {
            return Optional.ofNullable(hashValue(object).get(field));
        }
    }

    @Override
    public int removeHashFields(String key, List<String> fields) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return 0;
        requireType(key, object, RedisType.HASH);
        synchronized (object) {
            Map<String, String> hash = hashValue(object);
            int removed = 0;
            for (String field : fields) {
                if (hash.remove(field) != null) removed++;
            }
            if (hash.isEmpty()) values.remove(key, object);
            return removed;
        }
    }

    @Override
    public List<String> getAllHashFields(String key) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return List.of();
        requireType(key, object, RedisType.HASH);
        synchronized (object) {
            return hashValue(object).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    @Override
    public boolean hasHashField(String key, String field) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return false;
        requireType(key, object, RedisType.HASH);
        synchronized (object) {
            return hashValue(object).containsKey(field);
        }
    }

    @Override
    public int addSortedSetMembers(String key, List<String> scoreMembers) {
        requireKey(key);
        removeIfExpired(key);
        RedisObject object = values.computeIfAbsent(key,
                ignored -> new RedisObject(RedisType.ZSET, new SortedSetValue()));
        requireType(key, object, RedisType.ZSET);
        synchronized (object) {
            SortedSetValue sortedSet = sortedSetValue(object);
            int added = 0;
            for (int index = 0; index < scoreMembers.size(); index += 2) {
                double score = parseScore(scoreMembers.get(index), key);
                String member = scoreMembers.get(index + 1);
                if (!sortedSet.scores.containsKey(member)) added++;
                sortedSet.add(member, score);
            }
            return added;
        }
    }

    @Override
    public List<String> sortedSetRange(String key, int start, int stop) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return List.of();
        requireType(key, object, RedisType.ZSET);
        synchronized (object) {
            List<String> members = sortedSetValue(object).ordered.stream()
                    .map(SortedSetValue.Entry::member).toList();
            int from = normalizeIndex(start, members.size());
            int to = normalizeIndex(stop, members.size());
            if (from > to || from >= members.size() || to < 0) return List.of();
            from = Math.max(from, 0);
            to = Math.min(to, members.size() - 1);
            return List.copyOf(members.subList(from, to + 1));
        }
    }

    @Override
    public Optional<Double> sortedSetScore(String key, String member) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return Optional.empty();
        requireType(key, object, RedisType.ZSET);
        synchronized (object) {
            return Optional.ofNullable(sortedSetValue(object).scores.get(member));
        }
    }

    @Override
    public int removeSortedSetMembers(String key, List<String> members) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return 0;
        requireType(key, object, RedisType.ZSET);
        synchronized (object) {
            SortedSetValue sortedSet = sortedSetValue(object);
            int removed = 0;
            for (String member : members) {
                Double score = sortedSet.scores.remove(member);
                if (score != null) {
                    sortedSet.ordered.remove(new SortedSetValue.Entry(score, member));
                    removed++;
                }
            }
            if (sortedSet.scores.isEmpty()) values.remove(key, object);
            return removed;
        }
    }

    @Override
    public Optional<Integer> sortedSetRank(String key, String member) {
        removeIfExpired(key);
        RedisObject object = values.get(key);
        if (object == null) return Optional.empty();
        requireType(key, object, RedisType.ZSET);
        synchronized (object) {
            int rank = 0;
            for (SortedSetValue.Entry entry : sortedSetValue(object).ordered) {
                if (entry.member().equals(member)) return Optional.of(rank);
                rank++;
            }
            return Optional.empty();
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

    @SuppressWarnings("unchecked")
    private static Set<String> setValue(RedisObject object) {
        return (Set<String>) object.value();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> hashValue(RedisObject object) {
        return (Map<String, String>) object.value();
    }

    private static SortedSetValue sortedSetValue(RedisObject object) {
        return (SortedSetValue) object.value();
    }

    private static double parseScore(String value, String key) {
        try {
            double score = Double.parseDouble(value);
            if (Double.isNaN(score)) throw new NumberFormatException();
            return score;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid score '" + value + "' for key '" + key + "'");
        }
    }

    private static int normalizeIndex(int index, int size) {
        return index < 0 ? size + index : index;
    }

    @Override
    public boolean removeIfExpired(String key) {
        if (!expiration.isExpired(key)) return false;
        boolean removed = values.remove(key) != null;
        expiration.removeExpiry(key);
        return removed;
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
