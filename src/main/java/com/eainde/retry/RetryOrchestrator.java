package com.eainde.retry;

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

    public RetryOrchestrator(KafkaRetryService retryService,
                             RetryQualifierResolver qualifierResolver,
                             ExceptionRetryabilityChecker retryabilityChecker) {
        this.retryService = retryService;
        this.qualifierResolver = qualifierResolver;
        this.retryabilityChecker = retryabilityChecker;
    }

    /**
     * Processes a failed message exception, handling all retry logic internally.
     * @param exception The MessagingException caught by the error handler.
     */
    public void processFailure(MessagingException exception) {
        Message<?> failedMessage = exception.getFailedMessage();
        if (failedMessage == null) {
            logger.warn("Received a non-messaging exception on errorChannel, cannot process for retry.", exception);
            return;
        }

        String topic;
        RetryQualifierResolver.FailureContext context;
        Object payload = failedMessage.getPayload();

        // 1. Determine Context and Topic
        if (failedMessage.getHeaders().containsKey(KafkaHeaders.RECEIVED_TOPIC)) {
            context = RetryQualifierResolver.FailureContext.CONSUMER;
            topic = failedMessage.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC, String.class);
        } else if (failedMessage.getHeaders().containsKey(BinderHeaders.TARGET_DESTINATION)) {
            context = RetryQualifierResolver.FailureContext.PRODUCER;
            topic = failedMessage.getHeaders().get(BinderHeaders.TARGET_DESTINATION, String.class);
        } else {
            logger.error("Could not determine context for failed message. Headers: {}. Cannot retry.", failedMessage.getHeaders());
            return;
        }

        if (!(payload instanceof String)) {
            logger.error("Payload is not a String. Cannot process for retry. Payload type: {}", payload.getClass().getName());
            return;
        }

        // 2. Resolve Handler
        Optional<String> handlerQualifier = qualifierResolver.resolve(topic, context);
        if (handlerQualifier.isEmpty()) {
            logger.error("No handler mapping found for topic '{}' in context '{}'.", topic, context);
            return;
        }

        // 3. Check if the root cause is retryable
        Throwable cause = exception.getCause();
        String qualifier = handlerQualifier.get();

        if (retryabilityChecker.isRetryable(cause)) {
            logger.info("A retryable error occurred for topic '{}'. Saving for retry with handler '{}'.", topic, qualifier);
            if (context == RetryQualifierResolver.FailureContext.PRODUCER) {
                retryService.saveFailedProducerMessage((String) payload, qualifier);
            } else {
                retryService.saveFailedConsumerMessage((String) payload, qualifier);
            }
        } else {
            logger.error("A non-retryable error occurred for topic '{}'. Saving as PERMANENT_FAILURE for handler '{}'.", topic, qualifier, cause);
            if (context == RetryQualifierResolver.FailureContext.PRODUCER) {
                retryService.saveProducerMessageAsPermanentFailure((String) payload, qualifier);
            } else {
                retryService.saveConsumerMessageAsPermanentFailure((String) payload, qualifier);
            }
        }
    }
}
