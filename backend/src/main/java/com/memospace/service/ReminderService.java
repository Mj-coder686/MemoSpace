package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReminderService {
    private static final Set<String> KINDS = Set.of("BIRTHDAY", "ANNIVERSARY", "TASK", "PLAN", "CUSTOM");
    private static final Set<String> SCHEDULES = Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY");
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final FriendService friends;

    public ReminderService(JdbcTemplate jdbc, FriendService friends) {
        this.jdbc = jdbc;
        this.friends = friends;
    }

    public List<Map<String, Object>> list(long userId) {
        return queryVisible(userId, null).stream().map(row -> view(userId, row)).toList();
    }

    @Transactional
    public Map<String, Object> create(long creatorId, String title, String note, String reminderKind,
                                      String scheduleType, LocalDateTime remindAt, String timezone,
                                      Long imageFileId, Long relatedUserId, Long recipientUserId,
                                      Long relationshipId) {
        String kind = requireChoice(reminderKind, KINDS, "不支持的提醒类型");
        String schedule = requireChoice(scheduleType, SCHEDULES, "不支持的重复方式");
        ZoneId zone = requireZone(timezone);
        LocalDateTime firstTrigger = toDatabaseTime(remindAt.withNano(0), zone);
        if (recipientUserId != null && relationshipId != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "好友指派和关系共同提醒不能同时选择");
        }
        if (recipientUserId != null && recipientUserId == creatorId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "给自己的提醒不需要指定接收者");
        }
        requireOwnedImage(creatorId, imageFileId);

        Long effectiveRelatedUserId = relatedUserId;
        List<Long> relationshipMembers = List.of();
        if (relationshipId != null) {
            relationshipMembers = activeRelationshipMembers(creatorId, relationshipId);
            if (relatedUserId != null && !relationshipMembers.contains(relatedUserId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "相关用户不是该有效关系的成员");
            }
            if (effectiveRelatedUserId == null) {
                effectiveRelatedUserId = relationshipMembers.stream().filter(id -> id != creatorId).findFirst().orElse(null);
            }
        } else if (recipientUserId != null) {
            friends.requireActiveFriend(creatorId, recipientUserId);
            if (relatedUserId != null && !relatedUserId.equals(recipientUserId)) {
                friends.requireActiveFriend(creatorId, relatedUserId);
            }
            if (effectiveRelatedUserId == null) effectiveRelatedUserId = recipientUserId;
        } else if (relatedUserId != null) {
            friends.requireActiveFriend(creatorId, relatedUserId);
        }

        long reminderId = JdbcIds.insert(jdbc,
                "INSERT INTO reminder(creator_id,related_user_id,relationship_id,image_file_id,title,note," +
                        "reminder_kind,schedule_type,remind_at,next_trigger_at,timezone,status) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')",
                creatorId, effectiveRelatedUserId, relationshipId, imageFileId, title.trim(), clean(note),
                kind, schedule, firstTrigger, firstTrigger, zone.getId());

        if (relationshipId != null) {
            for (long memberId : relationshipMembers) {
                addParticipant(reminderId, memberId, memberId == creatorId ? "OWNER" : "RECIPIENT", "ACCEPTED", true);
            }
        } else if (recipientUserId != null) {
            addParticipant(reminderId, creatorId, "OWNER", "ACCEPTED", false);
            boolean direct = allowsDirectReminders(recipientUserId, creatorId);
            addParticipant(reminderId, recipientUserId, "RECIPIENT", direct ? "ACCEPTED" : "PENDING", true);
        } else {
            addParticipant(reminderId, creatorId, "OWNER", "ACCEPTED", true);
        }
        return detail(creatorId, reminderId);
    }

    @Transactional
    public Map<String, Object> accept(long userId, long reminderId) {
        Map<String, Object> participant = requireParticipantForUpdate(userId, reminderId);
        String acceptance = text(participant.get("acceptance_status"));
        if (!"PENDING".equals(acceptance)) {
            throw new ApiException(HttpStatus.CONFLICT, "该提醒不在待接受状态");
        }
        long creatorId = number(participant.get("creator_id"));
        friends.requireActiveFriend(userId, creatorId);
        jdbc.update("UPDATE reminder_participant SET acceptance_status='ACCEPTED',notifications_enabled=TRUE " +
                "WHERE reminder_id=? AND user_id=?", reminderId, userId);
        jdbc.update("UPDATE reminder SET status='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE id=?", reminderId);
        return detail(userId, reminderId);
    }

    @Transactional
    public Map<String, Object> reject(long userId, long reminderId) {
        Map<String, Object> participant = requireParticipantForUpdate(userId, reminderId);
        if (!"PENDING".equals(text(participant.get("acceptance_status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "该提醒不在待接受状态");
        }
        jdbc.update("UPDATE reminder_participant SET acceptance_status='REJECTED',notifications_enabled=FALSE " +
                "WHERE reminder_id=? AND user_id=?", reminderId, userId);
        int outstandingRecipients = count("SELECT COUNT(*) FROM reminder_participant WHERE reminder_id=? " +
                "AND participant_role='RECIPIENT' AND acceptance_status IN ('PENDING','ACCEPTED') AND completed_at IS NULL", reminderId);
        if (outstandingRecipients == 0) {
            jdbc.update("UPDATE reminder SET status='REJECTED',next_trigger_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", reminderId);
        }
        return detail(userId, reminderId);
    }

    @Transactional
    public Map<String, Object> complete(long userId, long reminderId) {
        Map<String, Object> participant = requireParticipantForUpdate(userId, reminderId);
        if (!"ACCEPTED".equals(text(participant.get("acceptance_status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "未接受的提醒不能标记完成");
        }
        if (participant.get("completed_at") == null) {
            jdbc.update("UPDATE reminder_participant SET completed_at=CURRENT_TIMESTAMP,notifications_enabled=FALSE " +
                    "WHERE reminder_id=? AND user_id=?", reminderId, userId);
        }
        int outstanding = count("SELECT COUNT(*) FROM reminder_participant WHERE reminder_id=? " +
                "AND acceptance_status IN ('PENDING','ACCEPTED') AND completed_at IS NULL " +
                "AND (notifications_enabled=TRUE OR acceptance_status='PENDING')", reminderId);
        if (outstanding == 0) {
            jdbc.update("UPDATE reminder SET status='COMPLETED',next_trigger_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", reminderId);
        }
        return detail(userId, reminderId);
    }

    @Transactional
    public Map<String, Object> snooze(long userId, long reminderId, LocalDateTime remindAt) {
        Map<String, Object> participant = requireParticipantForUpdate(userId, reminderId);
        if (!"ACCEPTED".equals(text(participant.get("acceptance_status"))) ||
                !truth(participant.get("notifications_enabled")) || participant.get("completed_at") != null) {
            throw new ApiException(HttpStatus.CONFLICT, "当前提醒不能稍后提醒");
        }
        if ("COMPLETED".equals(text(participant.get("status"))) || "REJECTED".equals(text(participant.get("status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "已结束的提醒不能稍后提醒");
        }
        ZoneId zone = requireZone(text(participant.get("timezone")));
        LocalDateTime next = toDatabaseTime(remindAt.withNano(0), zone);
        if (!next.isAfter(LocalDateTime.now(DATABASE_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "稍后提醒时间必须晚于当前时间");
        }
        jdbc.update("UPDATE reminder SET next_trigger_at=?,status='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE id=?", next, reminderId);
        return detail(userId, reminderId);
    }

    @Transactional
    public void delete(long userId, long reminderId) {
        List<Long> creators = jdbc.query("SELECT creator_id FROM reminder WHERE id=?", (rs, rowNum) -> rs.getLong(1), reminderId);
        if (creators.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提醒不存在");
        if (creators.get(0) != userId) throw new ApiException(HttpStatus.FORBIDDEN, "只有创建者可以删除该提醒");
        jdbc.update("DELETE FROM reminder WHERE id=?", reminderId);
    }

    public Map<String, Object> detail(long userId, long reminderId) {
        List<Map<String, Object>> rows = queryVisible(userId, reminderId);
        if (rows.isEmpty()) {
            if (count("SELECT COUNT(*) FROM reminder WHERE id=?", reminderId) == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "提醒不存在");
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "无权查看该提醒");
        }
        return view(userId, rows.get(0));
    }

    static LocalDateTime nextTrigger(String scheduleType, LocalDateTime anchorDatabaseTime,
                                     LocalDateTime afterDatabaseTime, String timezone) {
        if ("ONCE".equals(scheduleType)) return null;
        ZoneId zone = requireZone(timezone);
        LocalDateTime anchor = fromDatabaseTime(anchorDatabaseTime, zone);
        ZonedDateTime after = afterDatabaseTime.atZone(DATABASE_ZONE).withZoneSameInstant(zone);
        long amount = switch (scheduleType) {
            case "DAILY" -> Math.max(1, ChronoUnit.DAYS.between(anchor.toLocalDate(), after.toLocalDate()));
            case "WEEKLY" -> Math.max(1, ChronoUnit.DAYS.between(anchor.toLocalDate(), after.toLocalDate()) / 7);
            case "MONTHLY" -> Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(after)));
            case "YEARLY" -> Math.max(1, ChronoUnit.YEARS.between(anchor.toLocalDate(), after.toLocalDate()));
            default -> throw new IllegalArgumentException("Unsupported schedule type: " + scheduleType);
        };
        LocalDateTime candidate = occurrence(anchor, scheduleType, amount);
        while (!candidate.atZone(zone).toInstant().isAfter(after.toInstant())) {
            amount++;
            candidate = occurrence(anchor, scheduleType, amount);
        }
        return candidate.atZone(zone).withZoneSameInstant(DATABASE_ZONE).toLocalDateTime().withNano(0);
    }

    private List<Map<String, Object>> queryVisible(long userId, Long reminderId) {
        String sql = "SELECT r.*,creator.nickname AS creator_nickname,related.nickname AS related_user_nickname," +
                "current_participant.participant_role,current_participant.acceptance_status," +
                "current_participant.notifications_enabled,current_participant.last_notified_at,current_participant.completed_at," +
                "(SELECT rp2.user_id FROM reminder_participant rp2 WHERE rp2.reminder_id=r.id " +
                "AND rp2.participant_role='RECIPIENT' ORDER BY rp2.user_id LIMIT 1) AS recipient_user_id," +
                "(SELECT COUNT(*) FROM reminder_participant rp3 WHERE rp3.reminder_id=r.id) AS participant_count," +
                "(SELECT COUNT(*) FROM reminder_participant rp4 WHERE rp4.reminder_id=r.id AND rp4.acceptance_status='PENDING') AS pending_count " +
                "FROM reminder r JOIN user_account creator ON creator.id=r.creator_id " +
                "LEFT JOIN user_account related ON related.id=r.related_user_id " +
                "LEFT JOIN reminder_participant current_participant ON current_participant.reminder_id=r.id AND current_participant.user_id=? " +
                "WHERE (r.creator_id=? OR current_participant.user_id=?)";
        if (reminderId != null) {
            return jdbc.queryForList(sql + " AND r.id=?", userId, userId, userId, reminderId);
        }
        return jdbc.queryForList(sql + " ORDER BY CASE WHEN current_participant.acceptance_status='PENDING' THEN 0 ELSE 1 END," +
                "COALESCE(r.next_trigger_at,r.remind_at) ASC,r.id DESC", userId, userId, userId);
    }

    private Map<String, Object> view(long userId, Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        ZoneId zone = requireZone(text(source.get("timezone")));
        result.put("remind_at", displayTime(source.get("remind_at"), zone));
        result.put("next_trigger_at", displayTime(source.get("next_trigger_at"), zone));
        Object imageId = source.get("image_file_id");
        if (imageId != null) result.put("image_url", "/api/files/" + number(imageId) + "/content");
        boolean hasRecipient = source.get("recipient_user_id") != null;
        String scope = source.get("relationship_id") != null ? "RELATIONSHIP" : hasRecipient ? "ASSIGNED" :
                source.get("related_user_id") != null ? "PRIVATE_RELATED" : "PERSONAL";
        result.put("scope", scope);
        result.put("can_accept", "PENDING".equals(text(source.get("acceptance_status"))));
        result.put("can_delete", number(source.get("creator_id")) == userId);
        result.put("can_complete", "ACCEPTED".equals(text(source.get("acceptance_status"))) &&
                source.get("completed_at") == null && truth(source.get("notifications_enabled")));
        result.put("participants", participants(number(source.get("id"))));
        return result;
    }

    private List<Map<String, Object>> participants(long reminderId) {
        return jdbc.queryForList("SELECT rp.user_id,u.nickname,u.avatar,rp.participant_role,rp.acceptance_status," +
                "rp.notifications_enabled,rp.last_notified_at,rp.completed_at FROM reminder_participant rp " +
                "JOIN user_account u ON u.id=rp.user_id WHERE rp.reminder_id=? ORDER BY " +
                "CASE WHEN rp.participant_role='OWNER' THEN 0 ELSE 1 END,rp.user_id", reminderId);
    }

    private Map<String, Object> requireParticipantForUpdate(long userId, long reminderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.creator_id,r.status,r.timezone,rp.participant_role," +
                "rp.acceptance_status,rp.notifications_enabled,rp.completed_at FROM reminder r " +
                "LEFT JOIN reminder_participant rp ON rp.reminder_id=r.id AND rp.user_id=? WHERE r.id=? FOR UPDATE",
                userId, reminderId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "提醒不存在");
        Map<String, Object> row = rows.get(0);
        if (row.get("participant_role") == null) throw new ApiException(HttpStatus.FORBIDDEN, "无权处理该提醒");
        return row;
    }

    private List<Long> activeRelationshipMembers(long creatorId, long relationshipId) {
        List<Long> members = jdbc.query("SELECT rm.user_id FROM relationships r JOIN relationship_member rm " +
                        "ON rm.relationship_id=r.id WHERE r.id=? AND r.status='ACTIVE' ORDER BY rm.user_id",
                (rs, rowNum) -> rs.getLong(1), relationshipId);
        if (members.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "有效关系不存在");
        if (!members.contains(creatorId)) throw new ApiException(HttpStatus.FORBIDDEN, "你不是该有效关系的成员");
        if (members.size() < 2) throw new ApiException(HttpStatus.CONFLICT, "关系共同提醒至少需要双方成员");
        return members;
    }

    private void requireOwnedImage(long creatorId, Long imageFileId) {
        if (imageFileId == null) return;
        int ownedImages = count("SELECT COUNT(*) FROM file_record WHERE id=? AND owner_id=? AND mime_type LIKE 'image/%'",
                imageFileId, creatorId);
        if (ownedImages == 0) throw new ApiException(HttpStatus.FORBIDDEN, "提醒图片不存在或不属于你");
    }

    private boolean allowsDirectReminders(long recipientId, long creatorId) {
        List<Boolean> values = jdbc.query("SELECT allow_direct_reminders FROM friend_setting WHERE owner_id=? AND friend_id=?",
                (rs, rowNum) -> rs.getBoolean(1), recipientId, creatorId);
        return values.isEmpty() || values.get(0);
    }

    private void addParticipant(long reminderId, long userId, String role, String acceptance, boolean notifications) {
        jdbc.update("INSERT INTO reminder_participant(reminder_id,user_id,participant_role,acceptance_status,notifications_enabled) " +
                "VALUES(?,?,?,?,?)", reminderId, userId, role, acceptance, notifications);
    }

    private static LocalDateTime occurrence(LocalDateTime anchor, String scheduleType, long amount) {
        return switch (scheduleType) {
            case "DAILY" -> anchor.plusDays(amount);
            case "WEEKLY" -> anchor.plusWeeks(amount);
            case "MONTHLY" -> anchor.plusMonths(amount);
            case "YEARLY" -> anchor.plusYears(amount);
            default -> throw new IllegalArgumentException("Unsupported schedule type: " + scheduleType);
        };
    }

    private static ZoneId requireZone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效的时区");
        }
    }

    private static String requireChoice(String value, Set<String> choices, String message) {
        if (value == null || !choices.contains(value.trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim().toUpperCase();
    }

    private static LocalDateTime toDatabaseTime(LocalDateTime localTime, ZoneId zone) {
        return localTime.atZone(zone).withZoneSameInstant(DATABASE_ZONE).toLocalDateTime().withNano(0);
    }

    private static LocalDateTime fromDatabaseTime(LocalDateTime databaseTime, ZoneId zone) {
        return databaseTime.atZone(DATABASE_ZONE).withZoneSameInstant(zone).toLocalDateTime();
    }

    private static Object displayTime(Object value, ZoneId zone) {
        if (value == null) return null;
        LocalDateTime databaseTime = value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
        return fromDatabaseTime(databaseTime, zone).withNano(0);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static boolean truth(Object value) {
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
