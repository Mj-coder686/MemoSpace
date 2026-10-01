package com.memospace.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.memospace.api.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MobilePushService {
    private static final Logger log = LoggerFactory.getLogger(MobilePushService.class);
    private final JdbcTemplate jdbc;
    private final boolean configuredEnabled;
    private final String credentialsPath;
    private final String projectId;
    private volatile FirebaseMessaging messaging;

    public MobilePushService(JdbcTemplate jdbc,
                             @Value("${app.push.enabled:false}") boolean enabled,
                             @Value("${app.push.credentials-path:}") String credentialsPath,
                             @Value("${app.push.project-id:}") String projectId) {
        this.jdbc = jdbc;
        this.configuredEnabled = enabled;
        this.credentialsPath = credentialsPath == null ? "" : credentialsPath.trim();
        this.projectId = projectId == null ? "" : projectId.trim();
    }

    @PostConstruct
    void initialize() {
        if (!configuredEnabled) {
            log.info("Mobile push is disabled; in-app realtime notifications remain active");
            return;
        }
        if (credentialsPath.isBlank() || !Files.isRegularFile(Path.of(credentialsPath))) {
            log.warn("Mobile push is enabled but Firebase credentials are missing at {}", credentialsPath);
            return;
        }
        try (InputStream input = Files.newInputStream(Path.of(credentialsPath))) {
            FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(input));
            if (!projectId.isBlank()) options.setProjectId(projectId);
            FirebaseApp app = FirebaseApp.initializeApp(options.build(), "memospace-mobile-push");
            messaging = FirebaseMessaging.getInstance(app);
            log.info("Firebase mobile push initialized successfully");
        } catch (Exception ex) {
            log.error("Firebase mobile push could not be initialized", ex);
        }
    }

    public Map<String, Object> register(long userId, String token, String platform, String deviceName) {
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.length() < 20 || normalizedToken.length() > 512) throw new ApiException(HttpStatus.BAD_REQUEST, "推送设备令牌无效");
        String normalizedPlatform = platform == null ? "ANDROID" : platform.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ANDROID", "IOS").contains(normalizedPlatform)) throw new ApiException(HttpStatus.BAD_REQUEST, "暂不支持该设备平台");
        String safeDeviceName = deviceName == null ? null : deviceName.trim();
        if (safeDeviceName != null && safeDeviceName.length() > 120) safeDeviceName = safeDeviceName.substring(0, 120);
        int changed = jdbc.update("UPDATE mobile_push_device SET user_id=?,platform=?,device_name=?,enabled=TRUE,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE push_token=?",
                userId, normalizedPlatform, safeDeviceName, normalizedToken);
        if (changed == 0) jdbc.update("INSERT INTO mobile_push_device(user_id,push_token,platform,device_name) VALUES(?,?,?,?)", userId, normalizedToken, normalizedPlatform, safeDeviceName);
        return status(userId);
    }

    public void unregister(long userId, String token) {
        if (token == null || token.isBlank()) return;
        jdbc.update("DELETE FROM mobile_push_device WHERE user_id=? AND push_token=?", userId, token.trim());
    }

    public Map<String, Object> status(long userId) {
        Integer devices = jdbc.queryForObject("SELECT COUNT(*) FROM mobile_push_device WHERE user_id=? AND enabled=TRUE", Integer.class, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerConfigured", messaging != null);
        result.put("registeredDevices", devices == null ? 0 : devices);
        result.put("channel", "memospace_updates");
        return result;
    }

    @Async("pushExecutor")
    public void send(long userId, String type, String title, String content, long referenceId) {
        FirebaseMessaging client = messaging;
        if (client == null) return;
        List<String> tokens = jdbc.query("SELECT push_token FROM mobile_push_device WHERE user_id=? AND enabled=TRUE", (rs, rowNum) -> rs.getString(1), userId);
        for (String token : tokens) {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(content).build())
                    .putData("notificationType", type)
                    .putData("referenceId", Long.toString(referenceId))
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder().setChannelId("memospace_updates").setSound("default").build()).build())
                    .build();
            try {
                client.send(message);
                jdbc.update("UPDATE mobile_push_device SET last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE push_token=?", token);
            } catch (FirebaseMessagingException ex) {
                MessagingErrorCode code = ex.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    jdbc.update("UPDATE mobile_push_device SET enabled=FALSE,updated_at=CURRENT_TIMESTAMP WHERE push_token=?", token);
                } else log.warn("Push delivery failed for user {}: {}", userId, code);
            } catch (RuntimeException ex) {
                log.warn("Push delivery failed for user {}", userId, ex);
            }
        }
    }
}
