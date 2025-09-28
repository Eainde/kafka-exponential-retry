package com.eainde.retry.scheduler;

import com.eainde.retry.config.KafkaRetryProperties;
import com.eainde.retry.model.FailedMessage;
import com.eainde.retry.model.MessageStatus;
import com.eainde.retry.repository.FailedMessageRepository;
import com.eainde.retry.service.RetryMessageHandler;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class RetryScheduler {

    private final FailedMessageRepository failedMessageRepository;
    private final RetryMessageHandler retryMessageHandler;
    private final KafkaRetryProperties properties;

    public RetryScheduler(RetryMessageHandler retryMessageHandler, FailedMessageRepository failedMessageRepository, KafkaRetryProperties properties) {
        this.retryMessageHandler = retryMessageHandler;
        this.failedMessageRepository = failedMessageRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${kafka.retry.cron:0 * * * * *}")
    @SchedulerLock(name = "kafkaMessageRetryScheduler", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    public void processFailedMessages() {
        log.info("Starting failed message retry job.");

        LocalDateTime now = LocalDateTime.now();
        /*List<FailedMessage> messagesToRetry = failedMessageRepository.findAll().stream()
                .filter(msg -> msg.getStatus() == MessageStatus.FAILED)
                .filter(msg -> isReadyForRetry(msg, now))
                .toList();*/
        List<FailedMessage> messagesToRetry = failedMessageRepository.findMessagesToRetry(
                LocalDateTime.now(),
                properties.getInitialIntervalMinutes(),
                properties.getBatchSize(),
                properties.getMaxRetries()
        );

        if (messagesToRetry.isEmpty()) {
            log.info("No messages due for retry.");
            return;
        }

        log.info("Found {} messages to retry.", messagesToRetry.size());

        for (FailedMessage message : messagesToRetry) {
            processMessage(message);
        }

        log.info("Finished failed message retry job.");
    }

    private boolean isReadyForRetry(FailedMessage message, LocalDateTime now) {
        long intervalMinutes = (long) (properties.getInitialIntervalMinutes() * Math.pow(2, message.getRetryCount()));
        LocalDateTime nextAttemptTime = message.getLastAttemptTime().plusMinutes(intervalMinutes);
        return now.isAfter(nextAttemptTime);
    }

    private void processMessage(FailedMessage message) {
        try {
            retryMessageHandler.handle(message);
            message.setStatus(MessageStatus.PROCESSED);
            log.info("Successfully processed message ID: {}", message.getId());
        } catch (Exception e) {
            handleProcessingFailure(message, e);
        } finally {
            failedMessageRepository.save(message);
        }
    }

    private void handleProcessingFailure(FailedMessage message, Exception e) {
        log.warn("Failed to process message ID: {}. Error: {}", message.getId(), e.getMessage());
        message.setRetryCount(message.getRetryCount() + 1);
        message.setLastAttemptTime(LocalDateTime.now());
        message.setError(e.getMessage());

        if (message.getRetryCount() >= properties.getMaxRetries()) {
            message.setStatus(MessageStatus.PERMANENT_FAILURE);
            log.error("Message ID: {} has reached max retries ({}) and is marked as PERMANENT_FAILURE.",
                    message.getId(), properties.getMaxRetries());
        }
    }
}
