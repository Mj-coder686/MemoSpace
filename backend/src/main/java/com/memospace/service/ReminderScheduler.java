package com.memospace.service;

import com.memospace.realtime.RealtimeGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final ReminderDeliveryService delivery;
    private final ObjectProvider<RealtimeGateway> realtime;

    public ReminderScheduler(JdbcTemplate jdbc, ReminderDeliveryService delivery,
                             ObjectProvider<RealtimeGateway> realtime) {
        this.jdbc = jdbc;
        this.delivery = delivery;
        this.realtime = realtime;
    }

    @Scheduled(fixedDelayString = "${app.reminders.scan-delay-ms:5000}",
            initialDelayString = "${app.reminders.initial-delay-ms:5000}")
    public void scheduledScan() {
        scanDue(LocalDateTime.now(DATABASE_ZONE));
    }

    public int scanDue() {
        return scanDue(LocalDateTime.now(DATABASE_ZONE));
    }

    public int scanDue(LocalDateTime now) {
        List<Long> dueIds = jdbc.query("SELECT r.id FROM reminder r WHERE r.status='ACTIVE' " +
                        "AND r.next_trigger_at IS NOT NULL AND r.next_trigger_at<=? " +
                        "AND EXISTS (SELECT 1 FROM reminder_participant rp WHERE rp.reminder_id=r.id " +
                        "AND rp.acceptance_status='ACCEPTED' AND rp.notifications_enabled=TRUE AND rp.completed_at IS NULL) " +
                        "ORDER BY r.next_trigger_at,r.id LIMIT 100",
                (rs, rowNum) -> rs.getLong(1), now);
        int delivered = 0;
        for (long reminderId : dueIds) {
            try {
                List<ReminderDeliveryService.DeliveryEvent> events = delivery.deliver(reminderId, now);
                delivered += events.size();
                RealtimeGateway gateway = realtime.getIfAvailable();
                if (gateway != null) {
                    for (ReminderDeliveryService.DeliveryEvent event : events) {
                        try {
                            gateway.sendToUser(event.userId(), "REMINDER_DUE", event.payload());
                        } catch (RuntimeException ex) {
                            log.warn("Realtime reminder delivery failed for user {}", event.userId(), ex);
                        }
                    }
                }
            } catch (RuntimeException ex) {
                log.error("Reminder scan failed for reminder {}", reminderId, ex);
            }
        }
        return delivered;
    }
}
