package com.memospace.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReminderDeliveryService {
    private final JdbcTemplate jdbc;

    public ReminderDeliveryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<DeliveryEvent> deliver(long reminderId, LocalDateTime now) {
        List<Map<String, Object>> reminders = jdbc.queryForList(
                "SELECT id,creator_id,image_file_id,title,note,reminder_kind,schedule_type,remind_at," +
                        "next_trigger_at,timezone FROM reminder WHERE id=? AND status='ACTIVE' " +
                        "AND next_trigger_at IS NOT NULL AND next_trigger_at<=? FOR UPDATE", reminderId, now);
        if (reminders.isEmpty()) return List.of();

        Map<String, Object> reminder = reminders.get(0);
        LocalDateTime scheduledFor = time(reminder.get("next_trigger_at"));
        List<Long> participants = jdbc.query("SELECT user_id FROM reminder_participant WHERE reminder_id=? " +
                        "AND acceptance_status='ACCEPTED' AND notifications_enabled=TRUE AND completed_at IS NULL ORDER BY user_id",
                (rs, rowNum) -> rs.getLong(1), reminderId);
        if (participants.isEmpty()) return List.of();

        List<DeliveryEvent> events = new ArrayList<>();
        for (long userId : participants) {
            boolean inserted;
            try {
                jdbc.update("INSERT INTO reminder_delivery(reminder_id,user_id,scheduled_for,channel) VALUES(?,?,?,'IN_APP')",
                        reminderId, userId, scheduledFor);
                inserted = true;
            } catch (DuplicateKeyException ignored) {
                inserted = false;
            }
            if (!inserted) continue;

            String note = clean(reminder.get("note"));
            String content = note == null ? "提醒时间到了" : truncate(note, 300);
            jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                            "VALUES(?,?,'REMINDER_DUE',?,?,?)", userId, number(reminder.get("creator_id")),
                    truncate(String.valueOf(reminder.get("title")), 120), content, reminderId);
            jdbc.update("UPDATE reminder_participant SET last_notified_at=CURRENT_TIMESTAMP WHERE reminder_id=? AND user_id=?",
                    reminderId, userId);
            events.add(new DeliveryEvent(userId, payload(reminder, scheduledFor)));
        }

        String scheduleType = String.valueOf(reminder.get("schedule_type"));
        LocalDateTime next = ReminderService.nextTrigger(scheduleType, time(reminder.get("remind_at")), now,
                String.valueOf(reminder.get("timezone")));
        jdbc.update("UPDATE reminder SET next_trigger_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", next, reminderId);
        return events;
    }

    private Map<String, Object> payload(Map<String, Object> reminder, LocalDateTime scheduledFor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        long reminderId = number(reminder.get("id"));
        payload.put("reminderId", reminderId);
        payload.put("title", reminder.get("title"));
        payload.put("note", reminder.get("note"));
        payload.put("reminderKind", reminder.get("reminder_kind"));
        payload.put("scheduledFor", scheduledFor);
        payload.put("timezone", reminder.get("timezone"));
        if (reminder.get("image_file_id") != null) {
            long imageId = number(reminder.get("image_file_id"));
            payload.put("imageFileId", imageId);
            payload.put("imageUrl", "/api/files/" + imageId + "/content");
        }
        return payload;
    }

    private static LocalDateTime time(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    private static String clean(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    public record DeliveryEvent(long userId, Map<String, Object> payload) {}
}
