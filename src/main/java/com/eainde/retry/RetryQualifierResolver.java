package com.eainde.retry;

import com.eainde.retry.HandlerConfig;
import com.eainde.retry.config.KafkaRetryProperties;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Optional;

public class RetryQualifierResolver {
    // ... fields ...
    private final KafkaRetryProperties retryProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher(".");

    public enum FailureContext { PRODUCER, CONSUMER }

    public RetryQualifierResolver(KafkaRetryProperties retryProperties) {
        this.retryProperties = retryProperties;
    }

    public Optional<String> resolve(String topic, FailureContext context) {
        Map<String, HandlerConfig> contextMappings = getMappingsForContext(context);
        if (contextMappings == null) {
            return Optional.empty();
        }

        for (Map.Entry<String, HandlerConfig> entry : contextMappings.entrySet()) {
            String handlerName = entry.getKey();
            HandlerConfig handlerConfig = entry.getValue();
            if (handlerConfig != null && pathMatcher.match(handlerConfig.getTopic(), topic)) {
                return Optional.of(handlerName);
            }
        }
        return Optional.empty();
    }

    private Map<String, HandlerConfig> getMappingsForContext(FailureContext context) {
        if (retryProperties.getHandlerMappings() == null) {
            return null;
        }
        return context == FailureContext.CONSUMER ?
                retryProperties.getHandlerMappings().get("consumer") :
                retryProperties.getHandlerMappings().get("producer");
    }
}

