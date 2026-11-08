package com.peppeosmio.lockate.redis;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RedisService {
  private static final String NOTIFY_KEYSPACE_EVENTS = "notify-keyspace-events";

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

  /** Sets the value and returns the one it replaced, empty when the key didn't exist */
  public Optional<String> setAndGet(String key, String value, Duration ttl) {
    return Optional.ofNullable(redisTemplate.opsForValue().setGet(key, value, ttl));
  }

  /** Runs a Lua script atomically; empty when it returns nil */
  public Optional<String> runScript(String script, List<String> keys) {
    return Optional.ofNullable(redisTemplate.execute(RedisScript.of(script, String.class), keys));
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

  public void subscribePattern(String pattern, Consumer<String> onMessage) {
    container.addMessageListener(
        (message, p) -> onMessage.accept(new String(message.getBody(), StandardCharsets.UTF_8)),
        new PatternTopic(pattern));
  }

  /**
   * Turns on the events of expired keys, keeping the other events already enabled. Done here rather
   * than in the Valkey config, so a deployment that only updates the backend still gets them.
   */
  public void enableExpiredEvents() {
    try {
      redisTemplate.execute(
          (RedisCallback<Void>)
              connection -> {
                var current =
                    connection
                        .serverCommands()
                        .getConfig(NOTIFY_KEYSPACE_EVENTS)
                        .getProperty(NOTIFY_KEYSPACE_EVENTS, "");
                var flags = new StringBuilder(current);
                if (!current.contains("E")) {
                  flags.append('E');
                }
                // "A" is every event class, expired included
                if (!current.contains("x") && !current.contains("A")) {
                  flags.append('x');
                }
                connection.serverCommands().setConfig(NOTIFY_KEYSPACE_EVENTS, flags.toString());
                return null;
              });
    } catch (DataAccessException e) {
      log.warn("[Redis] Can't enable the expired keys events: " + e.getMessage());
    }
  }

  public void unsubscribe(String channel, MessageListener listener) {
    container.removeMessageListener(listener, new ChannelTopic(channel));
  }

  @PreDestroy
  public void cleanup() throws Exception {
    container.destroy();
  }
}
