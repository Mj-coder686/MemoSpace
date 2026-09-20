package com.memospace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memospace.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModerationService {
    private static final Set<String> TARGET_TYPES = Set.of("MEMORY", "COMMENT");
    private static final Set<String> REASONS = Set.of("ILLEGAL", "HARASSMENT", "PORNOGRAPHY", "VIOLENCE", "FRAUD", "PRIVACY", "OTHER");
    private static final Set<String> PENALTIES = Set.of("NONE", "WARNING", "MUTE_7_DAYS", "BAN");
    private final JdbcTemplate jdbc;
    private final PermissionService permission;
    private final ObjectMapper json;

    public ModerationService(JdbcTemplate jdbc, PermissionService permission, ObjectMapper json) {
        this.jdbc = jdbc;
        this.permission = permission;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> report(long reporterId, String rawType, long targetId, String rawReason, String description) {
        String type = normalize(rawType);
        String reason = normalize(rawReason);
        if (!TARGET_TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的举报对象");
        if (!REASONS.contains(reason)) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择有效的举报原因");

        Target target = target(reporterId, type, targetId);
        if (target.userId() == reporterId) throw new ApiException(HttpStatus.BAD_REQUEST, "不能举报自己的内容");
        String cleanDescription = description == null ? null : description.trim();
        try {
            long id = JdbcIds.insert(jdbc, "INSERT INTO content_report(reporter_id,reported_user_id,target_type,target_id,reason_category,description,target_snapshot) VALUES(?,?,?,?,?,?,?)",
                    reporterId, target.userId(), type, targetId, reason, cleanDescription, snapshot(target.payload()));
            return userReport(id, reporterId);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "你已经举报过这条内容，管理员正在处理");
        }
    }

    public List<Map<String, Object>> mine(long reporterId) {
        return jdbc.queryForList("SELECT id,target_type,target_id,reason_category,description,status,resolution_action,created_at,reviewed_at " +
                "FROM content_report WHERE reporter_id=? ORDER BY id DESC", reporterId);
    }

    public Map<String, Object> adminReports(String status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int offset = (safePage - 1) * safeSize;
        String normalized = status == null ? "" : normalize(status);
        String where = normalized.isBlank() || "ALL".equals(normalized) ? "" : " WHERE r.status=?";
        Object[] args = where.isBlank() ? new Object[]{} : new Object[]{normalized};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM content_report r" + where, Long.class, args);
        String sql = "SELECT r.id,r.target_type,r.target_id,r.reason_category,r.description,r.status,r.resolution_action,r.created_at,r.reviewed_at," +
                "reporter.nickname AS reporter_nickname,reported.id AS reported_user_id,reported.public_id AS reported_public_id," +
                "reported.username AS reported_username,reported.nickname AS reported_nickname,reported.account_status,reported.muted_until,reported.violation_count " +
                "FROM content_report r JOIN user_account reporter ON reporter.id=r.reporter_id " +
                "JOIN user_account reported ON reported.id=r.reported_user_id" + where + " ORDER BY CASE WHEN r.status='PENDING' THEN 0 ELSE 1 END,r.id DESC LIMIT ? OFFSET ?";
        Object[] queryArgs = new Object[args.length + 2];
        System.arraycopy(args, 0, queryArgs, 0, args.length);
        queryArgs[queryArgs.length - 2] = safeSize;
        queryArgs[queryArgs.length - 1] = offset;
        return Map.of("items", jdbc.queryForList(sql, queryArgs), "total", total == null ? 0L : total, "page", safePage, "size", safeSize);
    }

    public Map<String, Object> adminDetail(long reportId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.*,reporter.nickname AS reporter_nickname,reported.public_id AS reported_public_id," +
                "reported.username AS reported_username,reported.nickname AS reported_nickname,reported.account_status,reported.muted_until,reported.violation_count," +
                "reviewer.nickname AS reviewer_nickname FROM content_report r " +
                "JOIN user_account reporter ON reporter.id=r.reporter_id JOIN user_account reported ON reported.id=r.reported_user_id " +
                "LEFT JOIN user_account reviewer ON reviewer.id=r.reviewed_by WHERE r.id=?", reportId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "举报记录不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        String type = String.valueOf(result.get("target_type"));
        long targetId = ((Number) result.get("target_id")).longValue();
        List<Map<String, Object>> live = "MEMORY".equals(type)
                ? jdbc.queryForList("SELECT m.id,m.title,m.content,m.visibility,m.location,m.occurred_at,u.nickname AS creator_nickname FROM memory m JOIN user_account u ON u.id=m.creator_id WHERE m.id=?", targetId)
                : jdbc.queryForList("SELECT c.id,c.content,c.created_at,c.memory_id,m.title AS memory_title,u.nickname AS creator_nickname FROM comments c JOIN memory m ON m.id=c.memory_id JOIN user_account u ON u.id=c.user_id WHERE c.id=?", targetId);
        result.put("target", live.isEmpty() ? parseSnapshot(String.valueOf(result.get("target_snapshot"))) : live.get(0));
        if ("MEMORY".equals(type) && !live.isEmpty()) {
            List<Map<String, Object>> media = jdbc.queryForList("SELECT fr.id AS file_id,fr.mime_type,fr.original_name FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=? ORDER BY mm.sort_order", targetId);
            media.forEach(item -> item.put("content_url", "/api/admin/reports/" + reportId + "/media/" + item.get("file_id")));
            result.put("media", media);
        } else result.put("media", List.of());
        return result;
    }

    @Transactional
    public Map<String, Object> resolve(long adminId, long reportId, String decision, boolean removeContent, String rawPenalty, String note) {
        Map<String, Object> report = pendingReport(reportId);
        String normalizedDecision = normalize(decision);
        if ("DISMISS".equals(normalizedDecision)) {
            jdbc.update("UPDATE content_report SET status='DISMISSED',reviewed_by=?,resolution_action='DISMISS',admin_note=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                    adminId, clean(note), reportId);
            audit(adminId, number(report, "reported_user_id"), "DISMISS_REPORT", "驳回举报 #" + reportId);
            return adminDetail(reportId);
        }
        if (!"CONFIRM".equals(normalizedDecision)) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择确认违规或驳回举报");
        String penalty = normalize(rawPenalty);
        if (!PENALTIES.contains(penalty)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的处罚方式");

        long targetUserId = number(report, "reported_user_id");
        if (removeContent) deleteTarget(String.valueOf(report.get("target_type")), number(report, "target_id"));
        jdbc.update("UPDATE user_account SET violation_count=violation_count+1 WHERE id=?", targetUserId);
        applyPenalty(targetUserId, penalty);
        String action = (removeContent ? "DELETE+" : "") + penalty;
        jdbc.update("UPDATE content_report SET status='RESOLVED',reviewed_by=?,resolution_action=?,admin_note=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                adminId, action, clean(note), reportId);
        notifyUser(targetUserId, adminId, "社区内容处理通知", notice(removeContent, penalty, note));
        audit(adminId, targetUserId, "RESOLVE_REPORT", "处理举报 #" + reportId + "：" + action);
        return adminDetail(reportId);
    }

    @Transactional
    public Map<String, Object> setAccountStatus(long adminId, long userId, String rawAction, String note) {
        String action = normalize(rawAction);
        List<Map<String, Object>> accounts = jdbc.queryForList("SELECT is_admin FROM user_account WHERE id=?", userId);
        if (accounts.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        Object rawAdministrator = accounts.get(0).get("is_admin");
        boolean administrator = Boolean.TRUE.equals(rawAdministrator)
                || (rawAdministrator instanceof Number number && number.intValue() != 0);
        if (administrator) throw new ApiException(HttpStatus.BAD_REQUEST, "不能在这里处罚管理员账号");
        if (!Set.of("ACTIVE", "MUTE_7_DAYS", "BAN").contains(action)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的账号状态");
        if ("ACTIVE".equals(action)) jdbc.update("UPDATE user_account SET account_status='ACTIVE',muted_until=NULL WHERE id=?", userId);
        else applyPenalty(userId, action);
        notifyUser(userId, adminId, "账号状态通知", accountNotice(action, note));
        audit(adminId, userId, "ACCOUNT_STATUS", "账号状态调整为 " + action + (clean(note) == null ? "" : "：" + clean(note)));
        return jdbc.queryForMap("SELECT id,public_id,username,nickname,account_status,muted_until,violation_count FROM user_account WHERE id=?", userId);
    }

    public void requireCanLogin(long userId) {
        String status = jdbc.queryForObject("SELECT account_status FROM user_account WHERE id=?", String.class, userId);
        if ("BANNED".equals(status)) throw new ApiException(HttpStatus.FORBIDDEN, "账号已被封禁，请联系管理员");
    }

    public void requireCanPublish(long userId) {
        Map<String, Object> state = jdbc.queryForMap("SELECT account_status,muted_until FROM user_account WHERE id=?", userId);
        if ("BANNED".equals(String.valueOf(state.get("account_status")))) throw new ApiException(HttpStatus.FORBIDDEN, "账号已被封禁");
        Object rawMuted = state.get("muted_until");
        if (rawMuted instanceof java.sql.Timestamp timestamp && timestamp.toLocalDateTime().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "账号禁言至 " + timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
    }

    public boolean isBanned(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE id=? AND account_status='BANNED'", Integer.class, userId);
        return count != null && count > 0;
    }

    private Target target(long reporterId, String type, long id) {
        if ("MEMORY".equals(type)) {
            permission.requireView(reporterId, id);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,creator_id,title,content,visibility,location,occurred_at FROM memory WHERE id=?", id);
            if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "记忆不存在");
            return new Target(number(rows.get(0), "creator_id"), rows.get(0));
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id,c.user_id,c.content,c.created_at,c.memory_id,m.title AS memory_title FROM comments c JOIN memory m ON m.id=c.memory_id WHERE c.id=?", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "评论不存在");
        permission.requireView(reporterId, number(rows.get(0), "memory_id"));
        return new Target(number(rows.get(0), "user_id"), rows.get(0));
    }

    private Map<String, Object> userReport(long reportId, long reporterId) {
        return jdbc.queryForMap("SELECT id,target_type,target_id,reason_category,description,status,created_at FROM content_report WHERE id=? AND reporter_id=?", reportId, reporterId);
    }

    private Map<String, Object> pendingReport(long reportId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM content_report WHERE id=? FOR UPDATE", reportId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "举报记录不存在");
        if (!"PENDING".equals(String.valueOf(rows.get(0).get("status")))) throw new ApiException(HttpStatus.CONFLICT, "这条举报已经处理过了");
        return rows.get(0);
    }

    private void deleteTarget(String type, long targetId) {
        int changed = "MEMORY".equals(type)
                ? jdbc.update("DELETE FROM memory WHERE id=?", targetId)
                : jdbc.update("DELETE FROM comments WHERE id=?", targetId);
        if (changed == 0) throw new ApiException(HttpStatus.CONFLICT, "被举报内容已经不存在");
    }

    private void applyPenalty(long userId, String penalty) {
        if ("BAN".equals(penalty)) jdbc.update("UPDATE user_account SET account_status='BANNED',muted_until=NULL WHERE id=?", userId);
        else if ("MUTE_7_DAYS".equals(penalty)) jdbc.update("UPDATE user_account SET account_status='ACTIVE',muted_until=? WHERE id=?", LocalDateTime.now().plusDays(7), userId);
    }

    private void notifyUser(long userId, long adminId, String title, String content) {
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content) VALUES(?,?,'MODERATION',?,?)", userId, adminId, title, content);
    }

    private void audit(long adminId, long userId, String action, String detail) {
        jdbc.update("INSERT INTO admin_audit_log(admin_id,target_user_id,action_type,detail) VALUES(?,?,?,?)", adminId, userId, action, detail);
    }

    private String notice(boolean removed, String penalty, String note) {
        String result = removed ? "经核查，你发布的相关内容已被删除。" : "经核查，相关举报已确认违规。";
        if ("WARNING".equals(penalty)) result += " 本次给予正式警告。";
        else if ("MUTE_7_DAYS".equals(penalty)) result += " 账号已被禁言 7 天，期间只能浏览。";
        else if ("BAN".equals(penalty)) result += " 账号已被封禁。";
        return clean(note) == null ? result : result + " 管理员说明：" + clean(note);
    }

    private String accountNotice(String action, String note) {
        String result = switch (action) {
            case "ACTIVE" -> "账号限制已解除。";
            case "MUTE_7_DAYS" -> "账号已被禁言 7 天，期间只能浏览。";
            default -> "账号已被封禁。";
        };
        return clean(note) == null ? result : result + " 管理员说明：" + clean(note);
    }

    private String snapshot(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "无法保存举报证据"); }
    }

    private Object parseSnapshot(String value) {
        try { return json.readValue(value, Map.class); }
        catch (JsonProcessingException ex) { return Map.of("content", "历史举报证据无法解析"); }
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record Target(long userId, Map<String, Object> payload) {}
}
