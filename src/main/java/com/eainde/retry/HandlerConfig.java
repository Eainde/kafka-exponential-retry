package com.eainde.retry;

import java.util.ArrayList;
import java.util.List;

/**
 * A configuration object that holds all settings for a specific message handler,
 * including its topic pattern and custom exception policies.
 */
public class HandlerConfig {

    private String topic;
    private List<String> nonRetryableExceptions = new ArrayList<>();
    private List<String> retryableExceptions = new ArrayList<>();

    // --- Getters and Setters ---
    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getNonRetryableExceptions() {
        return nonRetryableExceptions;
    }

    public void setNonRetryableExceptions(List<String> nonRetryableExceptions) {
        this.nonRetryableExceptions = nonRetryableExceptions;
    }

    public List<String> getRetryableExceptions() {
        return retryableExceptions;
    }

    public void setRetryableExceptions(List<String> retryableExceptions) {
        this.retryableExceptions = retryableExceptions;
    }
}

