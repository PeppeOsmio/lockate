package com.peppeosmio.lockate.redis;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

  private final RedisTemplate<String, String> redisTemplate;
  private final RedisMessageListenerContainer container;

  public RedisService(RedisConnectionFactory connectionFactory) {
    // Configure RedisTemplate
    this.redisTemplate = new StringRedisTemplate();
    this.redisTemplate.setConnectionFactory(connectionFactory);
    this.redisTemplate.afterPropertiesSet();

    // Configure Listener Container
    this.container = new RedisMessageListenerContainer();
    this.container.setConnectionFactory(connectionFactory);
    // By default every message runs on a new thread, so frames could reach a socket out of order
    this.container.setTaskExecutor(newListenerExecutor());
    // It defaults to the task executor, and the subscription would hold its only thread
    this.container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor("redis-subscription-"));
    this.container.afterPropertiesSet();
    this.container.start();
  }

  static ThreadPoolTaskExecutor newListenerExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("redis-listener-");
    executor.initialize();
    return executor;
  }

  public void saveValue(String key, String value, Duration ttl) {
    redisTemplate.opsForValue().set(key, value, ttl);
  }

  public Optional<String> getValue(String key) {
    var result = redisTemplate.opsForValue().get(key);
    return Optional.ofNullable(result);
  }

  public void deleteValue(String key) {
    redisTemplate.delete(key);
  }

  /** Sets the value only if the key doesn't exist yet; returns whether it was set */
  public boolean setIfAbsent(String key, String value, Duration ttl) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
  }

  /** Atomically reads and deletes the key, so only one caller can ever get the value */
  public Optional<String> getAndDelete(String key) {
    return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key));
  }

  /**
   * Increments a counter, starting its expiry window on the first increment. INCR and EXPIRE are
   * separate commands, so a counter left without expiry gets one on the next call instead of
   * blocking forever.
   */
  public long increment(String key, Duration window) {
    var count = redisTemplate.opsForValue().increment(key);
    if (count != null && (count == 1 || redisTemplate.getExpire(key) == -1)) {
      redisTemplate.expire(key, window);
    }
    return count == null ? 0 : count;
  }

  public void publish(String channel, String message) {
    redisTemplate.convertAndSend(channel, message);
  }

  public MessageListener subscribe(String channel, Consumer<String> onMessage) {
    MessageListener listener =
        (message, pattern) -> {
          String payload = new String(message.getBody(), StandardCharsets.UTF_8);
          onMessage.accept(payload);
        };
    container.addMessageListener(listener, new ChannelTopic(channel));
    return listener;
  }

  public void unsubscribe(String channel, MessageListener listener) {
    container.removeMessageListener(listener, new ChannelTopic(channel));
  }

  @PreDestroy
  public void cleanup() throws Exception {
    container.destroy();
  }
}
