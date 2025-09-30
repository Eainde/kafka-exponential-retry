package com.eainde.retry;

import com.eainde.retry.config.KafkaRetryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A central service to determine if an exception is retryable based on the
 * configured properties (nonRetryableExceptions and retryableExceptions).
 */
public class ExceptionRetryabilityChecker {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionRetryabilityChecker.class);
    private final KafkaRetryProperties properties;

    public ExceptionRetryabilityChecker(KafkaRetryProperties properties) {
        this.properties = properties;
    }

    /**
     * Checks if a given exception is retryable based on the configuration.
     * It checks the entire class hierarchy of the exception.
     * @param throwable The exception or error that occurred.
     * @return true if the exception should be retried, false otherwise.
     */
    public boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return true; // Cannot determine, so default to retryable
        }

        // 1. Check against the non-retryable list first (blacklist takes precedence)
        for (String exClassName : properties.getNonRetryableExceptions()) {
            try {
                Class<?> exClass = Class.forName(exClassName);
                if (exClass.isInstance(throwable)) {
                    logger.debug("Exception {} matched non-retryable list entry '{}'.", throwable.getClass().getName(), exClassName);
                    return false; // Match found in blacklist, not retryable
                }
            } catch (ClassNotFoundException classNotFoundException) {
                logger.warn("Class not found in non-retryable exception list: {}", exClassName);
            }
        }

        // 2. If a retryable list is provided, it acts as a whitelist
        List<String> retryableExceptions = properties.getRetryableExceptions();
        if (retryableExceptions != null && !retryableExceptions.isEmpty()) {
            for (String exClassName : retryableExceptions) {
                try {
                    Class<?> exClass = Class.forName(exClassName);
                    if (exClass.isInstance(throwable)) {
                        logger.debug("Exception {} matched retryable list entry '{}'.", throwable.getClass().getName(), exClassName);
                        return true; // Match found in whitelist, is retryable
                    }
                } catch (ClassNotFoundException classNotFoundException) {
                    logger.warn("Class not found in retryable exception list: {}", exClassName);
                }
            }
            logger.debug("Exception {} did not match any entry in the configured retryable list.", throwable.getClass().getName());
            return false; // Whitelist exists, but no match was found
        }

        // 3. Default case: Not in the blacklist and no whitelist is configured, so retry.
        return true;
    }
}
