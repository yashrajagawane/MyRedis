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

    int addSet(String key, List<String> members);

    int removeSet(String key, List<String> members);

    List<String> setMembers(String key);

    boolean isSetMember(String key, String member);

    int setCardinality(String key);

    int putHashFields(String key, List<String> fieldValues);

    Optional<String> getHashField(String key, String field);

    int removeHashFields(String key, List<String> fields);

    List<String> getAllHashFields(String key);

    boolean hasHashField(String key, String field);

    int addSortedSetMembers(String key, List<String> scoreMembers);

    List<String> sortedSetRange(String key, int start, int stop);

    Optional<Double> sortedSetScore(String key, String member);

    int removeSortedSetMembers(String key, List<String> members);

    Optional<Integer> sortedSetRank(String key, String member);

    boolean removeIfExpired(String key);
}
