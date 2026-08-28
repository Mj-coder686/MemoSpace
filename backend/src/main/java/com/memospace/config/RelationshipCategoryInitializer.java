package com.memospace.config;

import com.memospace.service.RelationshipCategoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class RelationshipCategoryInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final RelationshipCategoryService categories;

    public RelationshipCategoryInitializer(JdbcTemplate jdbc, RelationshipCategoryService categories) {
        this.jdbc = jdbc;
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.queryForList("SELECT id FROM user_account").forEach(row ->
                categories.ensureDefaults(((Number) row.get("id")).longValue()));
        jdbc.queryForList("SELECT rm.relationship_id,rm.user_id,r.relationship_type FROM relationship_member rm " +
                "JOIN relationships r ON r.id=rm.relationship_id").forEach(row -> {
            long relationshipId = ((Number) row.get("relationship_id")).longValue();
            long userId = ((Number) row.get("user_id")).longValue();
            Long categoryId = categories.defaultCategoryId(userId, String.valueOf(row.get("relationship_type")));
            categories.linkIfMissing(categoryId, relationshipId);
        });
    }
}
