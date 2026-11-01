package com.peppeosmio.lockate.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RedisServiceTest {

  @Test
  void listenerExecutor_runsTasksOneAtATimeInSubmissionOrder() throws Exception {
    var executor = RedisService.newListenerExecutor();
    var ran = Collections.synchronizedList(new ArrayList<Integer>());
    var threads = Collections.synchronizedSet(new java.util.HashSet<String>());
    var done = new CountDownLatch(20);

    for (int i = 0; i < 20; i++) {
      int n = i;
      executor.execute(
          () -> {
            try {
              // earlier tasks are slower, so any parallelism would reorder them
              Thread.sleep(20 - n);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            threads.add(Thread.currentThread().getName());
            ran.add(n);
            done.countDown();
          });
    }

    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(ran).isEqualTo(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19));
    assertThat(threads).hasSize(1);
    executor.shutdown();
  }
}
