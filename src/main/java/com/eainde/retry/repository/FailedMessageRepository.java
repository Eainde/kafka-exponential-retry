package com.eainde.retry.repository;

import com.eainde.retry.model.FailedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedMessageRepository extends JpaRepository<FailedMessage, Long> {
    /**
     * Finds messages that are due for a retry based on exponential backoff logic.
     * A message is due if its last_attempt_time is older than the calculated next attempt time.
     *
     * Formula for next attempt time: last_attempt_time + (initial_interval * 2^retry_count)
     *
     * NOTE: This native query is written for Oracle DB syntax (12c+).
     *
     * @param currentTime The current time to compare against.
     * @param initialIntervalMinutes The configured base interval for retries.
     * @param maxRetries The maximum number of retries allowed.
     * @param batchSize The maximum number of rows to fetch.
     * @return A list of messages ready to be retried.
     */
    @Query(value = """
            SELECT * FROM failed_messages fm
            WHERE fm.status = 'FAILED'
            AND fm.retry_count < :maxRetries
            AND (
              fm.last_attempt_time IS NULL OR
              fm.last_attempt_time <= (:currentTime - NUMTODSINTERVAL((:initialInterval * POWER(2, fm.retry_count)), 'MINUTE'))
            )
            ORDER BY fm.last_attempt_time ASC NULLS FIRST
            FETCH FIRST :batchSize ROWS ONLY
            """, nativeQuery = true)
    List<FailedMessage> findMessagesToRetry(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("initialInterval") int initialIntervalMinutes,
            @Param("maxRetries") int maxRetries,
            @Param("batchSize") int batchSize);
}