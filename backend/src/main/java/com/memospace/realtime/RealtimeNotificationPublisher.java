package com.memospace.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** Publishes persisted notification events only after their database transaction commits. */
@Component
public class RealtimeNotificationPublisher {
    private final RealtimeGateway gateway;

    public RealtimeNotificationPublisher(RealtimeGateway gateway) {
        this.gateway = gateway;
    }

    public void publishAfterCommit(long userId, String notificationType, String title,
                                   String content, long referenceId) {
        Map<String, Object> payload = Map.of(
                "notificationType", notificationType,
                "title", title,
                "content", content,
                "referenceId", referenceId
        );
        Runnable publish = () -> gateway.sendToUser(userId, "NOTIFICATION", payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }
}
