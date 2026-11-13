package com.peppeosmio.lockate.relay;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Counts per key in one-minute windows, in memory: an instance that scales to zero or restarts
 * starts over, which only makes the limits a little looser.
 */
@ApplicationScoped
public class RateLimiter {
  static final long WINDOW_MILLIS = 60_000;

  private record Window(long startMillis, AtomicInteger count) {}

  private final LongSupplier now;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private volatile long lastPurgeMillis;

  public RateLimiter() {
    this(System::currentTimeMillis);
  }

  RateLimiter(LongSupplier now) {
    this.now = now;
    this.lastPurgeMillis = now.getAsLong();
  }

  public boolean allow(String key, int perMinute) {
    var t = now.getAsLong();
    purge(t);
    var window =
        windows.compute(
            key,
            (k, old) ->
                old == null || t - old.startMillis() >= WINDOW_MILLIS
                    ? new Window(t, new AtomicInteger())
                    : old);
    return window.count().incrementAndGet() <= perMinute;
  }

  int size() {
    return windows.size();
  }

  private void purge(long t) {
    if (t - lastPurgeMillis < WINDOW_MILLIS) {
      return;
    }
    lastPurgeMillis = t;
    windows.values().removeIf(window -> t - window.startMillis() >= WINDOW_MILLIS);
  }
}
