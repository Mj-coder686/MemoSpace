package com.memospace.config;

import com.memospace.domain.UserAccount;
import com.memospace.domain.UserMapper;
import com.memospace.service.JdbcIds;
import com.memospace.service.PublicIdService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Order(10)
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final UserMapper users;
    private final PasswordEncoder passwordEncoder;
    private final PublicIdService publicIds;
    private final boolean enabled;

    public DemoDataInitializer(JdbcTemplate jdbc, UserMapper users, PasswordEncoder passwordEncoder,
                               PublicIdService publicIds,
                               @Value("${app.demo-data.enabled}") boolean enabled) {
        this.jdbc = jdbc;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.publicIds = publicIds;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedThemes();
        if (!enabled || users.selectCount(null) > 0) return;

        UserAccount demo = newUser("demo", "拾光者", "在时间里收藏温柔的人。", "杭州");
        UserAccount mia = newUser("mia", "米娅", "喜欢日落、旅行和有风的下午。", "上海");

        long demoSpace = personalSpace(demo);
        long miaSpace = personalSpace(mia);
        long relationshipId = JdbcIds.insert(jdbc,
                "INSERT INTO relationships(relationship_type,status,established_at) VALUES('FRIEND','ACTIVE',?)",
                LocalDateTime.now().minusDays(428));
        jdbc.update("INSERT INTO relationship_member(relationship_id,user_id) VALUES(?,?),(?,?)",
                relationshipId, demo.getId(), relationshipId, mia.getId());
        long sharedSpace = JdbcIds.insert(jdbc,
                "INSERT INTO space(space_type,name,relationship_id,visibility,theme_id,status,created_at) VALUES('RELATIONSHIP',?,?,?,?, 'ACTIVE',?)",
                "我们的蓝色星球", relationshipId, "RELATIONSHIP", theme("Ocean Mist"), LocalDateTime.now().minusDays(428));
        jdbc.update("INSERT INTO space_member(space_id,user_id,member_role) VALUES(?,?,'MEMBER'),(?,?,'MEMBER')",
                sharedSpace, demo.getId(), sharedSpace, mia.getId());

        long m1 = memory(demo.getId(), "江边的晚风", "我们赶在日落前走到了江边，风里有夏天的味道。", "MIXED",
                LocalDateTime.now().minusDays(2).withHour(18).withMinute(40), "钱塘江边", "RELATIONSHIP");
        attach(m1, demoSpace, demo.getId());
        attach(m1, sharedSpace, demo.getId());

        long m2 = memory(mia.getId(), "早餐店的小窗口", "雨后的早晨，热咖啡和绿色的窗。", "PHOTO",
                LocalDateTime.now().minusDays(6).withHour(9).withMinute(10), "上海武康路", "PUBLIC");
        attach(m2, miaSpace, mia.getId());
        jdbc.update("INSERT INTO post(memory_id,creator_id,status) VALUES(?,?,'PUBLISHED')", m2, mia.getId());

        long m3 = memory(demo.getId(), "今天的三件小事", "读完一章书，整理了桌面，还看到了很淡的晚霞。", "TEXT",
                LocalDateTime.now().minusHours(3), "家", "PRIVATE");
        attach(m3, demoSpace, demo.getId());

        jdbc.update("INSERT INTO user_follow(follower_id,following_id) VALUES(?,?)", demo.getId(), mia.getId());
        jdbc.update("INSERT INTO anniversary(space_id,creator_id,title,anniversary_date) VALUES(?,?,?,?)",
                sharedSpace, demo.getId(), "我们认识的日子", java.time.LocalDate.now().minusDays(428));
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) VALUES(?,?,?,?,?,?)",
                demo.getId(), mia.getId(), "SPACE_MEMORY", "共同空间有新回忆", "米娅刚刚留下了一条温柔的记录。", m2);
    }

    private void seedThemes() {
        Object[][] themes = {
                {"Rose Mist", "#9C4F61", "#D69BA8", "#FFF8F5", "#FFFFFF", "#553F47", "#6B555D"},
                {"Ocean Mist", "#456C7E", "#7FA2B2", "#F5F7F6", "#FFFFFF", "#344E5A", "#566872"},
                {"Warm Home", "#875C35", "#C59E70", "#FFF9EF", "#FFFDFA", "#60452F", "#735A45"},
                {"Midnight Mist", "#5D6478", "#9299AD", "#F7F6F4", "#FFFFFF", "#373A45", "#646672"},
                {"Lavender Dream", "#675F82", "#9990B0", "#F8F5FA", "#FFFFFF", "#443E55", "#6C6678"},
                {"Forest Memory", "#4F6C59", "#819887", "#F4F6F1", "#FFFFFF", "#35463A", "#5E6961"},
                {"Sunset Film", "#8C5947", "#BD8A74", "#FFF7F1", "#FFFFFF", "#543C34", "#745B51"},
                {"Coffee Diary", "#6E5140", "#9D806C", "#F8F3ED", "#FFFFFF", "#44352D", "#68584F"}
        };
        for (Object[] t : themes) {
            int updated = jdbc.update("UPDATE space_theme SET primary_color=?,secondary_color=?,background_color=?,surface_color=?,text_color=?,muted_color=? WHERE preset_name=?",
                    t[1], t[2], t[3], t[4], t[5], t[6], t[0]);
            if (updated == 0) {
                jdbc.update("INSERT INTO space_theme(preset_name,primary_color,secondary_color,background_color,surface_color,text_color,muted_color) VALUES(?,?,?,?,?,?,?)", t);
            }
        }
    }

    private UserAccount newUser(String username, String nickname, String bio, String location) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Memo123!"));
        user.setNickname(nickname);
        user.setBio(bio);
        user.setLocation(location);
        user.setCreatedAt(LocalDateTime.now().minusDays(500));
        user.setUpdatedAt(LocalDateTime.now());
        publicIds.insertWithPublicId(user);
        return user;
    }

    private long personalSpace(UserAccount user) {
        long id = JdbcIds.insert(jdbc,
                "INSERT INTO space(space_type,name,owner_id,visibility,theme_id,status) VALUES('PERSONAL',?,?,?,?, 'ACTIVE')",
                user.getNickname() + "的拾光空间", user.getId(), "PRIVATE", theme("Midnight Mist"));
        jdbc.update("INSERT INTO space_member(space_id,user_id,member_role) VALUES(?,?,'OWNER')", id, user.getId());
        return id;
    }

    private long memory(long creator, String title, String content, String type, LocalDateTime occurred, String location, String visibility) {
        return JdbcIds.insert(jdbc,
                "INSERT INTO memory(creator_id,title,content,memory_type,occurred_at,location,visibility) VALUES(?,?,?,?,?,?,?)",
                creator, title, content, type, occurred, location, visibility);
    }

    private void attach(long memoryId, long spaceId, long userId) {
        jdbc.update("INSERT INTO memory_space(memory_id,space_id,added_by) VALUES(?,?,?)", memoryId, spaceId, userId);
    }

    private Long theme(String name) {
        return jdbc.queryForObject("SELECT id FROM space_theme WHERE preset_name=?", Long.class, name);
    }
}
