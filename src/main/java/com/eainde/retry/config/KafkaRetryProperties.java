package com.eainde.retry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
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

    /**
     * A map to link a logical handler bean name (the key) to a Kafka topic name pattern (the value).
     * This is used to find the correct handler for messages from dynamic or complex topic names.
     * Example: kycProcessApprovalHandler -> cl.uk.*.kyc-procee-approval.rt
     */
    private Map<String, String> handlerMappings = new HashMap<>();
}
