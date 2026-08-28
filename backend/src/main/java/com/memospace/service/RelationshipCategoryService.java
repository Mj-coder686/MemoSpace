package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RelationshipCategoryService {
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("LOVER", "恋人", "heart", "Rose Mist", 10),
            new DefaultCategory("BUDDY", "死党", "handshake", "Ocean Mist", 20),
            new DefaultCategory("BESTIE", "闺蜜", "sparkles", "Lavender Dream", 30),
            new DefaultCategory("FAMILY", "家人", "home", "Warm Home", 40)
    );
    private static final Set<String> ICONS = Set.of("heart", "handshake", "sparkles", "home", "users", "coffee", "camera", "star", "leaf");
    private final JdbcTemplate jdbc;

    public RelationshipCategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void ensureDefaults(long userId) {
        for (DefaultCategory item : DEFAULTS) {
            if (count("SELECT COUNT(*) FROM relationship_category WHERE owner_id=? AND category_key=?", userId, item.key()) > 0) continue;
            Long themeId = themeId(item.theme());
            JdbcIds.insert(jdbc,
                    "INSERT INTO relationship_category(owner_id,category_key,name,icon,category_type,theme_id,is_visible,sort_order) " +
                            "VALUES(?,?,?,?, 'SYSTEM',?,TRUE,?)",
                    userId, item.key(), item.name(), item.icon(), themeId, item.order());
        }
    }

    public List<Map<String, Object>> list(long userId, boolean includeHidden) {
        ensureDefaults(userId);
        String hiddenClause = includeHidden ? "" : " AND c.is_visible=TRUE";
        return jdbc.queryForList("SELECT c.id,c.category_key,c.name,c.icon,c.category_type,c.is_visible,c.sort_order,c.theme_id," +
                        "t.preset_name,t.primary_color,t.secondary_color,t.background_color,t.surface_color,t.text_color,t.muted_color," +
                        "(SELECT COUNT(*) FROM relationship_category_link l JOIN relationships r ON r.id=l.relationship_id " +
                        " WHERE l.category_id=c.id AND r.status='ACTIVE') AS relationship_count " +
                        "FROM relationship_category c LEFT JOIN space_theme t ON t.id=c.theme_id " +
                        "WHERE c.owner_id=?" + hiddenClause + " ORDER BY c.sort_order,c.id", userId);
    }

    public Map<String, Object> detail(long userId, long categoryId) {
        Map<String, Object> category = ownedCategory(userId, categoryId);
        Map<String, Object> result = new LinkedHashMap<>(category);
        result.put("people", people(userId, categoryId));
        return result;
    }

    public List<Map<String, Object>> people(long userId, long categoryId) {
        ownedCategory(userId, categoryId);
        return jdbc.queryForList("SELECT r.id AS relationship_id,r.relationship_type,r.established_at,r.status AS relationship_status," +
                        "other_user.id AS user_id,other_user.username,other_user.nickname,other_user.avatar,other_user.bio,other_user.location," +
                        "s.id AS space_id,s.name AS space_name,s.status AS space_status,s.cover_url," +
                        "(SELECT COUNT(*) FROM memory_space ms WHERE ms.space_id=s.id) AS memory_count " +
                        "FROM relationship_category_link l " +
                        "JOIN relationships r ON r.id=l.relationship_id AND r.status='ACTIVE' " +
                        "JOIN relationship_member mine ON mine.relationship_id=r.id AND mine.user_id=? " +
                        "JOIN relationship_member theirs ON theirs.relationship_id=r.id AND theirs.user_id<>? " +
                        "JOIN user_account other_user ON other_user.id=theirs.user_id " +
                        "LEFT JOIN space s ON s.relationship_id=r.id " +
                        "WHERE l.category_id=? ORDER BY r.established_at DESC,other_user.nickname",
                userId, userId, categoryId);
    }

    @Transactional
    public Map<String, Object> createCustom(long userId, String name, String icon, Long themeId) {
        ensureDefaults(userId);
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "分类名称不能为空");
        if (count("SELECT COUNT(*) FROM relationship_category WHERE owner_id=? AND LOWER(name)=LOWER(?)", userId, normalizedName) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "已经存在同名关系分类");
        }
        String safeIcon = ICONS.contains(icon) ? icon : "users";
        Long selectedTheme = validateTheme(themeId);
        Integer nextOrder = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+10 FROM relationship_category WHERE owner_id=?", Integer.class, userId);
        long id = JdbcIds.insert(jdbc,
                "INSERT INTO relationship_category(owner_id,category_key,name,icon,category_type,theme_id,is_visible,sort_order) VALUES(?,?,?,?, 'CUSTOM',?,TRUE,?)",
                userId, "CUSTOM_" + UUID.randomUUID().toString().replace("-", ""), normalizedName, safeIcon, selectedTheme, nextOrder);
        return ownedCategory(userId, id);
    }

    @Transactional
    public Map<String, Object> setVisible(long userId, long categoryId, boolean visible) {
        ownedCategory(userId, categoryId);
        jdbc.update("UPDATE relationship_category SET is_visible=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?", visible, categoryId, userId);
        return ownedCategory(userId, categoryId);
    }

    @Transactional
    public List<Map<String, Object>> reorder(long userId, List<Long> categoryIds) {
        ensureDefaults(userId);
        if (categoryIds == null || categoryIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "排序列表不能为空");
        List<Long> distinct = categoryIds.stream().distinct().toList();
        int owned = count("SELECT COUNT(*) FROM relationship_category WHERE owner_id=? AND id IN (" + placeholders(distinct.size()) + ")",
                prepend(userId, distinct));
        if (owned != distinct.size()) throw new ApiException(HttpStatus.FORBIDDEN, "排序中包含不属于你的分类");
        int order = 10;
        for (Long id : distinct) {
            jdbc.update("UPDATE relationship_category SET sort_order=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND owner_id=?", order, id, userId);
            order += 10;
        }
        return list(userId, true);
    }

    public List<Map<String, Object>> relationships(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.id,r.relationship_type,r.status,r.established_at,r.archived_at," +
                        "u.id AS user_id,u.username,u.nickname,u.avatar,u.bio,u.location,s.id AS space_id,s.name AS space_name,s.status AS space_status " +
                        "FROM relationships r JOIN relationship_member mine ON mine.relationship_id=r.id AND mine.user_id=? " +
                        "JOIN relationship_member theirs ON theirs.relationship_id=r.id AND theirs.user_id<>? " +
                        "JOIN user_account u ON u.id=theirs.user_id LEFT JOIN space s ON s.relationship_id=r.id " +
                        "ORDER BY CASE WHEN r.status='ACTIVE' THEN 0 ELSE 1 END,r.established_at DESC", userId, userId);
        for (Map<String, Object> row : rows) {
            long relationshipId = ((Number) row.get("id")).longValue();
            row.put("categories", jdbc.queryForList("SELECT c.id,c.name,c.icon,c.category_key,c.is_visible FROM relationship_category_link l " +
                    "JOIN relationship_category c ON c.id=l.category_id WHERE l.relationship_id=? AND c.owner_id=? ORDER BY c.sort_order", relationshipId, userId));
        }
        return rows;
    }

    @Transactional
    public List<Map<String, Object>> replaceRelationshipCategories(long userId, long relationshipId, List<Long> categoryIds) {
        if (count("SELECT COUNT(*) FROM relationship_member WHERE relationship_id=? AND user_id=?", relationshipId, userId) == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权管理该关系");
        }
        if (categoryIds == null || categoryIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "关系至少需要保留一个分类标签");
        List<Long> distinct = categoryIds.stream().distinct().toList();
        int owned = count("SELECT COUNT(*) FROM relationship_category WHERE owner_id=? AND id IN (" + placeholders(distinct.size()) + ")",
                prepend(userId, distinct));
        if (owned != distinct.size()) throw new ApiException(HttpStatus.FORBIDDEN, "分类标签不属于当前用户");
        List<Long> ownedCategoryIds = jdbc.query("SELECT id FROM relationship_category WHERE owner_id=?",
                (rs, rowNum) -> rs.getLong(1), userId);
        if (!ownedCategoryIds.isEmpty()) {
            List<Object> deleteArgs = new ArrayList<>();
            deleteArgs.add(relationshipId);
            deleteArgs.addAll(ownedCategoryIds);
            jdbc.update("DELETE FROM relationship_category_link WHERE relationship_id=? AND category_id IN (" +
                    placeholders(ownedCategoryIds.size()) + ")", deleteArgs.toArray());
        }
        for (Long id : distinct) jdbc.update("INSERT INTO relationship_category_link(category_id,relationship_id) VALUES(?,?)", id, relationshipId);
        return jdbc.queryForList("SELECT c.id,c.name,c.icon,c.category_key,c.is_visible FROM relationship_category_link l " +
                "JOIN relationship_category c ON c.id=l.category_id WHERE l.relationship_id=? AND c.owner_id=? ORDER BY c.sort_order", relationshipId, userId);
    }

    public Map<String, Object> ownedCategory(long userId, long categoryId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.id,c.owner_id,c.category_key,c.name,c.icon,c.category_type,c.theme_id,c.is_visible,c.sort_order," +
                "t.preset_name,t.primary_color,t.secondary_color,t.background_color,t.surface_color,t.text_color,t.muted_color " +
                "FROM relationship_category c LEFT JOIN space_theme t ON t.id=c.theme_id WHERE c.id=? AND c.owner_id=?", categoryId, userId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "关系分类不存在");
        return rows.get(0);
    }

    @Transactional
    public long categoryForReceiver(long receiverId, Map<String, Object> snapshot) {
        ensureDefaults(receiverId);
        String key = String.valueOf(snapshot.get("category_key"));
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM relationship_category WHERE owner_id=? AND category_key=?", receiverId, key);
        if (!existing.isEmpty()) return ((Number) existing.get(0).get("id")).longValue();
        Integer nextOrder = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+10 FROM relationship_category WHERE owner_id=?", Integer.class, receiverId);
        return JdbcIds.insert(jdbc,
                "INSERT INTO relationship_category(owner_id,category_key,name,icon,category_type,theme_id,is_visible,sort_order) VALUES(?,?,?,?, 'CUSTOM',?,TRUE,?)",
                receiverId, key, snapshot.get("category_name"), snapshot.get("category_icon"), snapshot.get("theme_id"), nextOrder);
    }

    public void linkIfMissing(long categoryId, long relationshipId) {
        if (count("SELECT COUNT(*) FROM relationship_category_link WHERE category_id=? AND relationship_id=?", categoryId, relationshipId) == 0) {
            jdbc.update("INSERT INTO relationship_category_link(category_id,relationship_id) VALUES(?,?)", categoryId, relationshipId);
        }
    }

    public Long defaultCategoryId(long userId, String relationshipType) {
        ensureDefaults(userId);
        String key = switch (relationshipType) {
            case "COUPLE" -> "LOVER";
            case "FAMILY" -> "FAMILY";
            default -> "BUDDY";
        };
        return jdbc.queryForObject("SELECT id FROM relationship_category WHERE owner_id=? AND category_key=?", Long.class, userId, key);
    }

    private Long validateTheme(Long themeId) {
        if (themeId == null) return themeId("Midnight Mist");
        if (count("SELECT COUNT(*) FROM space_theme WHERE id=?", themeId) == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "主题不存在");
        return themeId;
    }

    private Long themeId(String name) {
        List<Long> values = jdbc.query("SELECT id FROM space_theme WHERE preset_name=?", (rs, rowNum) -> rs.getLong(1), name);
        return values.isEmpty() ? null : values.get(0);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Object[] prepend(long first, List<Long> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.addAll(rest);
        return args.toArray();
    }

    private record DefaultCategory(String key, String name, String icon, String theme, int order) {}
}
