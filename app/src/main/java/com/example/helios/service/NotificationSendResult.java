package com.example.helios.service;

public class NotificationSendResult {
    private final int recipientCount;

    public NotificationSendResult(int recipientCount) {
        this.recipientCount = Math.max(0, recipientCount);
    }

    public int getRecipientCount() {
        return recipientCount;
    }
}
