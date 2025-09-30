package com.eainde.retry;

import com.eainde.retry.service.KafkaRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

import java.util.Optional;

/**
 * A central orchestrator service that acts as the single entry point for handling Kafka failures.
 * This encapsulates the logic of determining failure context, checking for retryability,
 * and routing the message to the correct persistence service.
 * This is the primary bean that consuming applications should interact with.
 */
public class RetryOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(RetryOrchestrator.class);

    private final KafkaRetryService retryService;
    private final RetryQualifierResolver qualifierResolver;
    private final ExceptionRetryabilityChecker retryabilityChecker;

    /**
     * A private record to hold the extracted details of a failed message.
     * This simplifies passing context between private methods.
     */
    private record FailureDetails(String topic, RetryQualifierResolver.FailureContext context) {}

    public RetryOrchestrator(KafkaRetryService retryService,
                             RetryQualifierResolver qualifierResolver,
                             ExceptionRetryabilityChecker retryabilityChecker) {
        this.retryService = retryService;
        this.qualifierResolver = qualifierResolver;
        this.retryabilityChecker = retryabilityChecker;
    }

    /**
     * The main public method to process a failed message exception.
     * It orchestrates the flow of determining context, resolving handlers, and saving the message.
     * @param exception The MessagingException caught by the application's error handler.
     */
    public void processFailure(MessagingException exception) {
        Message<?> failedMessage = exception.getFailedMessage();
        if (failedMessage == null) {
            logger.warn("Received a non-messaging exception on errorChannel, cannot process for retry.", exception);
            return;
        }

        // Step 1: Extract topic and context (producer/consumer) from the message headers.
        Optional<FailureDetails> detailsOptional = determineFailureDetails(failedMessage);
        if (detailsOptional.isEmpty()) {
            return; // Error is logged within the private method.
        }
        FailureDetails details = detailsOptional.get();

        // Step 2: Ensure the payload is a String before proceeding.
        if (!(failedMessage.getPayload() instanceof String payload)) {
            logger.error("Payload is not a String. Cannot process for retry. Payload type: {}", failedMessage.getPayload().getClass().getName());
            return;
        }

        // Step 3: Resolve the handler and process the failure based on exception policies.
        resolveAndProcess(exception.getCause(), details, payload);
    }

    /**
     * Determines the failure context (PRODUCER or CONSUMER) and the target topic
     * by inspecting the headers of the failed message.
     * @param failedMessage The message that failed.
     * @return An Optional containing the FailureDetails, or empty if context cannot be determined.
     */
    private Optional<FailureDetails> determineFailureDetails(Message<?> failedMessage) {
        if (failedMessage.getHeaders().containsKey(KafkaHeaders.RECEIVED_TOPIC)) {
            String topic = failedMessage.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC, String.class);
            return Optional.of(new FailureDetails(topic, RetryQualifierResolver.FailureContext.CONSUMER));
        }
        if (failedMessage.getHeaders().containsKey(BinderHeaders.TARGET_DESTINATION)) {
            String topic = failedMessage.getHeaders().get(BinderHeaders.TARGET_DESTINATION, String.class);
            return Optional.of(new FailureDetails(topic, RetryQualifierResolver.FailureContext.PRODUCER));
        }
        logger.error("Could not determine context for failed message. Headers: {}. Cannot retry.", failedMessage.getHeaders());
        return Optional.empty();
    }

    /**
     * Resolves the handler, checks for retryability, and routes the message to the correct
     * persistence method (either for retry or for permanent failure).
     * @param cause The root cause exception of the failure.
     * @param details The context of the failure (topic and producer/consumer).
     * @param payload The message payload to save.
     */
    private void resolveAndProcess(Throwable cause, FailureDetails details, String payload) {
        Optional<String> handlerQualifierOpt = qualifierResolver.resolve(details.topic(), details.context());

        if (handlerQualifierOpt.isEmpty()) {
            logger.error("No handler mapping found for topic '{}' in context '{}'.", details.topic(), details.context());
            return;
        }
        String qualifier = handlerQualifierOpt.get();

        // Check the configured exception policies to decide the message's fate.
        if (retryabilityChecker.isRetryable(cause, qualifier, details.context())) {
            saveForRetry(payload, qualifier, details);
        } else {
            saveAsPermanentFailure(cause, payload, qualifier, details);
        }
    }

    /**
     * Persists a message to the appropriate database table with a 'FAILED' status
     * so it can be picked up by the schedulers.
     * @param payload The message payload.
     * @param qualifier The resolved handler name.
     * @param details The context of the failure.
     */
    private void saveForRetry(String payload, String qualifier, FailureDetails details) {
        logger.info("A retryable error occurred for topic '{}'. Saving for retry with handler '{}'.", details.topic(), qualifier);
        if (details.context() == RetryQualifierResolver.FailureContext.PRODUCER) {
            retryService.saveFailedProducerMessage(payload, qualifier);
        } else {
            retryService.saveFailedConsumerMessage(payload, qualifier);
        }
    }

    /**
     * Persists a message to the appropriate database table with a 'PERMANENT_FAILURE' status.
     * This message will be ignored by the schedulers.
     * @param cause The exception that caused the failure.
     * @param payload The message payload.
     * @param qualifier The resolved handler name.
     * @param details The context of the failure.
     */
    private void saveAsPermanentFailure(Throwable cause, String payload, String qualifier, FailureDetails details) {
        logger.error("A non-retryable error occurred for topic '{}'. Saving as PERMANENT_FAILURE for handler '{}'.", details.topic(), qualifier, cause);
        if (details.context() == RetryQualifierResolver.FailureContext.PRODUCER) {
            retryService.saveProducerMessageAsPermanentFailure(payload, qualifier);
        } else {
            retryService.saveConsumerMessageAsPermanentFailure(payload, qualifier);
        }
    }
}
