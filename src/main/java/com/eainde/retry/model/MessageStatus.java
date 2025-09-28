package com.eainde.retry.model;

public enum MessageStatus {
    /**
     * The message has failed processing and is awaiting retry.
     */
    FAILED,

    /**
     * The message was successfully processed after one or more retries.
     */
    PROCESSED,

    /**
     * The message has exceeded the maximum number of retries and will not be attempted again.
     */
    PERMANENT_FAILURE
}