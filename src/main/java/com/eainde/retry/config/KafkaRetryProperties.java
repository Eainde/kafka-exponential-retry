package com.eainde.retry.config;

import com.eainde.retry.HandlerConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the Kafka retry mechanism.
 * Allows microservices to configure retry behavior via application.yml.
 */
@Data
@ConfigurationProperties(prefix = "kafka.retry")
public class KafkaRetryProperties {

    /**
     * Whether to enable the retry scheduler.
     */
    private boolean enabled = false;

    /**
     * The initial interval in minutes for the first retry.
     */
    private int initialIntervalMinutes = 5;

    /**
     * The maximum number of retry attempts before marking a message as a permanent failure.
     */
    private int maxRetries = 5;

    /**
     * Cron expression for the scheduler. Defaults to running every minute.
     */
    private String cron = "0 * * * * *";


    /**
     * The maximum number of records to fetch from the database in a single retry batch.
     */
    private int batchSize = 100;
    // Global exception lists that act as a default or fallback.
    private List<String> nonRetryableExceptions = new ArrayList<>();
    private List<String> retryableExceptions = new ArrayList<>();

    /**
     * Contains the mappings from logical handler bean names to Kafka topic patterns.
     * This is now structured to separate consumer and producer handlers.
     */
    private Map<String, Map<String, HandlerConfig>> handlerMappings = new HashMap<>();

    /**
     * A nested class to hold separate mappings for consumer and producer failures.
     */
    public static class HandlerMappings {
        private Map<String, String> consumer = new HashMap<>();
        private Map<String, String> producer = new HashMap<>();

        public Map<String, String> getConsumer() {
            return consumer;
        }

        public void setConsumer(Map<String, String> consumer) {
            this.consumer = consumer;
        }

        public Map<String, String> getProducer() {
            return producer;
        }

        public void setProducer(Map<String, String> producer) {
            this.producer = producer;
        }
    }
}
