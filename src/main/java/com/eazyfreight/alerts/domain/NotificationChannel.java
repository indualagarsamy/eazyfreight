package com.eazyfreight.alerts.domain;

/** How a notification reaches its recipient. */
public enum NotificationChannel {
    IN_APP("In-app"),
    EMAIL("Email"),
    SMS("SMS");

    private final String label;

    NotificationChannel(String label) {
        this.label = label;
    }

    /** For audit notes, which are read by people rather than parsed. */
    public String label() {
        return label;
    }
}
