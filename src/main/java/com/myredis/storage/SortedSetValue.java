package com.myredis.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

final class SortedSetValue {
    final Map<String, Double> scores = new HashMap<>();
    final TreeSet<Entry> ordered = new TreeSet<>();

    void add(String member, double score) {
        Double oldScore = scores.put(member, score);
        if (oldScore != null) ordered.remove(new Entry(oldScore, member));
        ordered.add(new Entry(score, member));
    }

    record Entry(double score, String member) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            int scoreComparison = Double.compare(score, other.score);
            return scoreComparison != 0 ? scoreComparison : member.compareTo(other.member);
        }
    }
}
