package com.hsf302.trainoffice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisTripListenerConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisTripListenerConfig.class);

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        try {
            container.setConnectionFactory(connectionFactory);
        } catch (Exception ex) {
            log.warn("[TripLifecycle] Could not set RedisConnectionFactory on container: {}", ex.getMessage());
        }
        return container;
    }
}
