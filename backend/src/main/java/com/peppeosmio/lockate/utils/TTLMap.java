package com.peppeosmio.lockate.utils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;

public class TTLMap<K, V> {
    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Thread> ttlThreads = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TTLMap(Duration ttl) {
        this.ttl = ttl;
    }

    public void put(K key, V value) {
        // Cancel old expiration thread if present
        Thread oldThread = ttlThreads.remove(key);
        if (oldThread != null) {
            oldThread.interrupt();
        }

        map.put(key, value);

        // Create a new virtual thread for TTL expiration
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(ttl.toMillis());
                map.remove(key);
                ttlThreads.remove(key);
            } catch (InterruptedException ignored) {
                // Key was replaced or removed before expiration
            }
        });

        ttlThreads.put(key, t);
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(map.get(key));
    }

    public void remove(K key) {
        map.remove(key);
        Thread t = ttlThreads.remove(key);
        if (t != null) {
            t.interrupt();
        }
    }

    public int size() {
        return map.size();
    }
}

