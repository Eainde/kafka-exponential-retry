package com.eainde.retry.service;

import com.eainde.retry.model.FailedMessage;

/**
 * An interface that consuming microservices must implement.
 * The logic within the 'handle' method will be executed for each retry attempt.
 */
@FunctionalInterface
public interface RetryMessageHandler {

    /**
     * Processes the payload of a failed message.
     * The implementation should contain the original consumer logic.
     * If this method throws an exception, the retry is considered a failure.
     *
     * @param payload The raw string payload of the message.
     * @throws Exception if processing fails.
     */
    void handle(FailedMessage payload) throws Exception;
}
