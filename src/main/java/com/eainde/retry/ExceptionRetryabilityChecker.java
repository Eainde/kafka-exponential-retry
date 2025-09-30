package com.eainde.retry;

import com.eainde.retry.HandlerConfig;
import com.eainde.retry.config.KafkaRetryProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExceptionRetryabilityChecker {

    private final KafkaRetryProperties properties;

    public ExceptionRetryabilityChecker(KafkaRetryProperties properties) {
        this.properties = properties;
    }

    public boolean isRetryable(Throwable exception, String handlerQualifier, RetryQualifierResolver.FailureContext context) {
        Optional<HandlerConfig> handlerConfig = getHandlerConfig(handlerQualifier, context);

        // Rule 1: Check non-retryable lists (blacklist). Handler-specific takes precedence.
        if (exceptionMatches(exception, handlerConfig.map(HandlerConfig::getNonRetryableExceptions).orElse(List.of())) ||
                exceptionMatches(exception, properties.getNonRetryableExceptions())) {
            return false;
        }

        // Rule 2: Check retryable lists (whitelist) if they exist.
        List<String> handlerRetryable = handlerConfig.map(HandlerConfig::getRetryableExceptions).orElse(List.of());
        if (!handlerRetryable.isEmpty()) {
            return exceptionMatches(exception, handlerRetryable);
        }

        List<String> globalRetryable = properties.getRetryableExceptions();
        if (!globalRetryable.isEmpty()) {
            return exceptionMatches(exception, globalRetryable);
        }

        // Rule 3: If no whitelists are defined, retry everything not on a blacklist.
        return true;
    }

    private Optional<HandlerConfig> getHandlerConfig(String handlerQualifier, RetryQualifierResolver.FailureContext context) {
        Map<String, HandlerConfig> contextMappings = context == RetryQualifierResolver.FailureContext.CONSUMER ?
                properties.getHandlerMappings().get("consumer") :
                properties.getHandlerMappings().get("producer");

        return Optional.ofNullable(contextMappings).map(m -> m.get(handlerQualifier));
    }

    private boolean exceptionMatches(Throwable exception, List<String> exceptionClassNames) {
        if (exception == null || exceptionClassNames == null || exceptionClassNames.isEmpty()) {
            return false;
        }
        return exceptionClassNames.stream()
                .anyMatch(name -> {
                    try {
                        return Class.forName(name).isInstance(exception);
                    } catch (ClassNotFoundException e) {
                        return false;
                    }
                });
    }
}
