package com.eainde.retry.service;

import com.eainde.retry.model.FailedMessage;
import com.eainde.retry.repository.FailedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A service for client applications to easily persist failed Kafka messages.
 */
@Service
public class KafkaRetryService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRetryService.class);

    private final FailedMessageRepository repository;

    public KafkaRetryService(FailedMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves a raw message payload to the database for later reprocessing.
     *
     * @param payload The string content of the failed Kafka message.
     */
    public void saveFailedMessage(String payload) {
        try {
            FailedMessage failedMessage = new FailedMessage();
            failedMessage.setPayload(payload);
            repository.save(failedMessage);
            logger.info("Saved failed Kafka message to the database for retry.");
        } catch (Exception e) {
            logger.error("Failed to save Kafka message to the retry database.", e);
        }
    }
}
