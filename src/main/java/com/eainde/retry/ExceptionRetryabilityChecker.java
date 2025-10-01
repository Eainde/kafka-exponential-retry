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

    /**
     * Checks if a given exception is retryable based on the configured policies
     * for the specific handler and context.
     * @param cause The root cause exception of the failure.
     * @param handlerQualifier The name of the handler bean responsible for the message.
     * @param context The failure context (PRODUCER or CONSUMER).
     * @return true if the exception is retryable, false otherwise.
     */
    /**
     * Checks if a given exception is retryable based on the configured policies
     * for the specific handler and context.
     * @param cause The root cause exception of the failure.
     * @param handlerQualifier The name of the handler bean responsible for the message.
     * @param context The failure context (PRODUCER or CONSUMER).
     * @return true if the exception is retryable, false otherwise.
     */
    public boolean isRetryable(Throwable cause, String handlerQualifier, RetryQualifierResolver.FailureContext context) {
        if (cause == null) {
            logger.warn("Exception cause is null for handler '{}'. Considering it retryable by default.", handlerQualifier);
            return true; // Default to retry if the cause is unknown.
        }

        List<String> nonRetryableExceptions;
        List<String> retryableExceptions;

        // Step 1: Find the most specific configuration available (handler-specific or global).
        HandlerConfig handlerConfig = findHandlerConfig(handlerQualifier, context);

        if (handlerConfig != null) {
            logger.debug("Using handler-specific exception rules for handler '{}' in context '{}'.", handlerQualifier, context);
            nonRetryableExceptions = handlerConfig.getNonRetryableExceptions();
            retryableExceptions = handlerConfig.getRetryableExceptions();
        } else {
            logger.debug("No handler-specific rules found for '{}'. Falling back to global exception rules.", handlerQualifier);
            nonRetryableExceptions = properties.getNonRetryableExceptions();
            retryableExceptions = properties.getRetryableExceptions();
        }

        // Ensure lists are not null to prevent NullPointerExceptions.
        nonRetryableExceptions = nonRetryableExceptions != null ? nonRetryableExceptions : Collections.emptyList();
        retryableExceptions = retryableExceptions != null ? retryableExceptions : Collections.emptyList();

        // Step 2: Check the blacklist first, as it always has the highest precedence.
        if (isExceptionInList(cause, nonRetryableExceptions)) {
            logger.warn("Exception {} for handler '{}' is in the NON-RETRYABLE list. Decision: DO NOT RETRY.", cause.getClass().getName(), handlerQualifier);
            return false;
        }

        // Step 3: If a whitelist is defined, the exception MUST be on it to be retried.
        if (!retryableExceptions.isEmpty()) {
            if (isExceptionInList(cause, retryableExceptions)) {
                logger.info("Exception {} for handler '{}' is in the RETRYABLE list. Decision: RETRY.", cause.getClass().getName(), handlerQualifier);
                return true;
            } else {
                logger.warn("Exception {} for handler '{}' is NOT in the defined RETRYABLE list. Decision: DO NOT RETRY.", cause.getClass().getName(), handlerQualifier);
                return false;
            }
        }

        // Step 4: If no whitelist is defined and it's not on the blacklist, retry by default.
        logger.info("Exception {} for handler '{}' is not blacklisted and no specific whitelist is defined. Decision: RETRY by default.", cause.getClass().getName(), handlerQualifier);
        return true;
    }

    /**
     * Finds the specific HandlerConfig for a given handler and context from the application properties.
     * This implementation assumes the `handler-mappings` property is a Map where the keys
     * are "consumer" and "producer".
     * @param handlerQualifier The name of the handler bean.
     * @param context The failure context (PRODUCER or CONSUMER).
     * @return The HandlerConfig if found, otherwise null.
     */
    private HandlerConfig findHandlerConfig(String handlerQualifier, RetryQualifierResolver.FailureContext context) {
        if (properties.getHandlerMappings() == null) {
            return null;
        }

        // Determine which map to look in ("consumer" or "producer") based on the context.
        String contextKey = context == RetryQualifierResolver.FailureContext.CONSUMER ? "consumer" : "producer";
        Map<String, HandlerConfig> contextMappings = properties.getHandlerMappings().get(contextKey);

        if (contextMappings == null) {
            return null;
        }

        // Look up the specific handler configuration within the correct context map.
        return contextMappings.get(handlerQualifier);
    }


    /**
     * Checks if a given Throwable is an instance of any of the class names in the provided list.
     * @param cause The exception to check.
     * @param exceptionList A list of fully qualified class names.
     * @return true if the exception is an instance of a class in the list, false otherwise.
     */
    private boolean isExceptionInList(Throwable cause, List<String> exceptionList) {
        return exceptionList.stream()
                .anyMatch(exceptionClassName -> {
                    try {
                        Class<?> exceptionClass = Class.forName(exceptionClassName);
                        // isInstance() correctly handles subclasses.
                        return exceptionClass.isInstance(cause);
                    } catch (ClassNotFoundException e) {
                        logger.error("Configured exception class not found on classpath: {}", exceptionClassName, e);
                        return false;
                    }
                });
    }
}
