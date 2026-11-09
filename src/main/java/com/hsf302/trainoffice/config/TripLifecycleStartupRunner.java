package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.service.TripLifecycleRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class TripLifecycleStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TripLifecycleStartupRunner.class);
    private final TripLifecycleRedisService tripLifecycleRedisService;

    public TripLifecycleStartupRunner(TripLifecycleRedisService tripLifecycleRedisService) {
        this.tripLifecycleRedisService = tripLifecycleRedisService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tripLifecycleRedisService.scanAndSyncAllTripsOnStartup();
        } catch (Exception ex) {
            log.error("[TripLifecycle] Startup scan encountered an error but continuing application startup: {}", ex.getMessage(), ex);
        }
    }
}
