package com.peppeosmio.lockate.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TTLMapTest {

  @Test
  void put_thenGet_returnsValue() {
    var map = new TTLMap<String, String>(Duration.ofSeconds(5));

    map.put("key", "value");

    assertThat(map.get("key")).contains("value");
  }

  @Test
  void get_missingKey_returnsEmpty() {
    var map = new TTLMap<String, String>(Duration.ofSeconds(5));

    assertThat(map.get("missing")).isEmpty();
  }

  @Test
  void remove_deletesValue() {
    var map = new TTLMap<String, String>(Duration.ofSeconds(5));
    map.put("key", "value");

    map.remove("key");

    assertThat(map.get("key")).isEmpty();
    assertThat(map.size()).isZero();
  }

  @Test
  void size_reflectsNumberOfEntries() {
    var map = new TTLMap<String, String>(Duration.ofSeconds(5));

    map.put("a", "1");
    map.put("b", "2");

    assertThat(map.size()).isEqualTo(2);
  }

  @Test
  void rePut_resetsTtl_valueSurvivesPastOriginalTtl() throws InterruptedException {
    var map = new TTLMap<String, String>(Duration.ofMillis(150));

    map.put("key", "first");
    Thread.sleep(100);
    map.put("key", "second");
    Thread.sleep(100);

    assertThat(map.get("key")).contains("second");
  }

  @Test
  void expiry_removesEntryAfterTtl() throws InterruptedException {
    var map = new TTLMap<String, String>(Duration.ofMillis(100));

    map.put("key", "value");

    boolean expired = false;
    for (int i = 0; i < 50 && !expired; i++) {
      TimeUnit.MILLISECONDS.sleep(20);
      expired = map.get("key").isEmpty();
    }

    assertThat(expired).isTrue();
  }
}
