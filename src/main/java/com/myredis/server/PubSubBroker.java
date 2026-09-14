package com.myredis.server;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/** Isolates channel subscriptions and bounded message delivery from key storage. */
public final class PubSubBroker {
    public static final int MAX_PENDING_MESSAGES = 1_024;
    private final ConcurrentHashMap<String, Set<BlockingQueue<List<String>>>> subscribers = new ConcurrentHashMap<>();

    public int subscribe(String channel, BlockingQueue<List<String>> queue) {
        subscribers.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet()).add(queue);
        return subscriptionCount(queue);
    }

    public int unsubscribe(String channel, BlockingQueue<List<String>> queue) {
        Set<BlockingQueue<List<String>>> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers != null) {
            channelSubscribers.remove(queue);
            if (channelSubscribers.isEmpty()) subscribers.remove(channel, channelSubscribers);
        }
        return subscriptionCount(queue);
    }

    public int unsubscribeAll(BlockingQueue<List<String>> queue) {
        subscribers.forEach((channel, queues) -> {
            queues.remove(queue);
            if (queues.isEmpty()) subscribers.remove(channel, queues);
        });
        return 0;
    }

    public int publish(String channel, String message) {
        Set<BlockingQueue<List<String>>> queues = subscribers.get(channel);
        if (queues == null) return 0;
        int delivered = 0;
        List<String> event = List.of("message", channel, message);
        for (BlockingQueue<List<String>> queue : queues) {
            if (queue.offer(event)) delivered++;
        }
        return delivered;
    }

    private int subscriptionCount(BlockingQueue<List<String>> queue) {
        int count = 0;
        for (Set<BlockingQueue<List<String>>> queues : subscribers.values()) {
            if (queues.contains(queue)) count++;
        }
        return count;
    }
}
