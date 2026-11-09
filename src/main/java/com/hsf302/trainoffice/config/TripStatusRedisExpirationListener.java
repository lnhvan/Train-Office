package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.service.TripLifecycleRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TripStatusRedisExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TripStatusRedisExpirationListener.class);
    private final TripLifecycleRedisService tripLifecycleRedisService;

    public TripStatusRedisExpirationListener(RedisMessageListenerContainer listenerContainer,
                                             TripLifecycleRedisService tripLifecycleRedisService) {
        super(listenerContainer);
        this.tripLifecycleRedisService = tripLifecycleRedisService;
        try {
            setKeyspaceNotificationsConfigParameter("Ex");
        } catch (Exception ex) {
            log.warn("[TripLifecycle] Could not configure notify-keyspace-events: {}", ex.getMessage());
        }
    }

    @Override
    public void init() {
        try {
            super.init();
            log.info("[TripLifecycle] Redis KeyExpirationEventMessageListener registered successfully.");
        } catch (Exception ex) {
            log.warn("[TripLifecycle] Could not register Redis key expiration listener (Redis may be offline): {}", ex.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            tripLifecycleRedisService.handleExpiredKey(expiredKey);
        } catch (Exception ex) {
            log.error("[TripLifecycle] Error processing expired key {}: {}", expiredKey, ex.getMessage());
        }
    }
}
