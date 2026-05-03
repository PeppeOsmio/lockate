package com.peppeosmio.lockate.redis;

import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

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
        this.container.afterPropertiesSet();
        this.container.start();
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

    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    public MessageListener subscribe(String channel, Consumer<String> onMessage) {
        MessageListener listener = (message, pattern) -> {
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
