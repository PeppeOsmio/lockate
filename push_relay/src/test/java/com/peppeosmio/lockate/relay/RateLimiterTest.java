package com.peppeosmio.lockate.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {
  private long now = 0;
  private final RateLimiter limiter = new RateLimiter(() -> now);

  @Test
  void allowsUpToTheLimitInAMinute() {
    assertTrue(limiter.allow("a", 2));
    assertTrue(limiter.allow("a", 2));
    assertFalse(limiter.allow("a", 2));
  }

  @Test
  void keysCountApart() {
    assertTrue(limiter.allow("a", 1));
    assertTrue(limiter.allow("b", 1));
  }

  @Test
  void aNewMinuteStartsOver() {
    limiter.allow("a", 1);
    now = RateLimiter.WINDOW_MILLIS;

    assertTrue(limiter.allow("a", 1));
  }

  @Test
  void oldWindowsAreForgotten() {
    limiter.allow("a", 1);
    limiter.allow("b", 1);
    now = RateLimiter.WINDOW_MILLIS;

    limiter.allow("c", 1);

    assertEquals(1, limiter.size());
  }
}
