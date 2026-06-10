package com.sparta.one_stop.global.outbox.exception;

public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }

}
