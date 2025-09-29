package com.eainde.retry;

import com.eainde.retry.config.KafkaRetryProperties;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Optional;

/**
 * A central service to resolve a Kafka topic name to a logical handler bean name (qualifier).
 * It uses the mappings configured in kafka.retry.handler-mappings.
 */
public class RetryQualifierResolver {

    private final KafkaRetryProperties retryProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher("."); // Use dot as a separator

    public RetryQualifierResolver(KafkaRetryProperties retryProperties) {
        this.retryProperties = retryProperties;
    }

    /**
     * Matches the incoming topic against the patterns defined in application.yml
     * to find the correct logical handler bean name.
     * @param topic The actual topic name from the Kafka message.
     * @return An Optional containing the logical handler name if a match is found.
     */
    public Optional<String> resolve(String topic) {
        if (topic == null) {
            return Optional.empty();
        }

        Map<String, String> mappings = retryProperties.getHandlerMappings();
        if (mappings == null) {
            return Optional.empty();
        }

        // Iterate through the map of {handlerName -> topicPattern}
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String handlerName = entry.getKey();
            String topicPattern = entry.getValue();
            if (pathMatcher.match(topicPattern, topic)) {
                return Optional.of(handlerName); // Return the logical handler name
            }
        }
        return Optional.empty(); // No pattern matched the topic
    }
}
