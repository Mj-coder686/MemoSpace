package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memospace.service.ReminderScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReminderScheduler scheduler;

    @Test
    void friendAssignmentHonorsRecipientConsentAndReminderImageAccess() throws Exception {
        Account creator = register("reminder_creator", "提醒创建者");
        Account recipient = register("reminder_recipient", "提醒接收者");
        Account outsider = register("reminder_outsider", "无关用户");
        makeFriends(creator, recipient);

        putJson("/api/friends/" + creator.id() + "/settings",
                Map.of("allowDirectReminders", false), recipient.token(), 200);
        long imageId = upload(creator.token(), "reminder.png");

        Map<String, Object> request = reminderRequest("待确认的生日提醒", LocalDateTime.now().plusDays(2));
        request.put("recipientUserId", recipient.id());
        request.put("imageFileId", imageId);
        JsonNode created = postJson("/api/reminders", request, creator.token(), 200);
        long reminderId = created.get("id").asLong();

        assertEquals("ASSIGNED", created.get("scope").asText());
        assertEquals("PENDING", participant(created, recipient.id()).get("acceptance_status").asText());
        assertTrue(getJson("/api/reminders", recipient.token(), 200).toString().contains("待确认的生日提醒"));
        expectPng(recipient.token(), imageId);
        mvc.perform(get("/api/files/{id}/content", imageId).header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden());

        JsonNode accepted = postJson("/api/reminders/" + reminderId + "/accept", Map.of(), recipient.token(), 200);
        assertEquals("ACCEPTED", accepted.get("acceptance_status").asText());
        JsonNode snoozed = postJson("/api/reminders/" + reminderId + "/snooze",
                Map.of("remindAt", LocalDateTime.now().plusDays(3).withNano(0)), recipient.token(), 200);
        assertTrue(snoozed.hasNonNull("next_trigger_at"));
        JsonNode completed = postJson("/api/reminders/" + reminderId + "/complete", Map.of(), recipient.token(), 200);
        assertEquals("COMPLETED", completed.get("status").asText());

        long rejectedImageId = upload(creator.token(), "rejected-reminder.png");
        Map<String, Object> rejectedRequest = reminderRequest("拒绝后不再展示图片", LocalDateTime.now().plusDays(5));
        rejectedRequest.put("recipientUserId", recipient.id());
        rejectedRequest.put("imageFileId", rejectedImageId);
        JsonNode rejectedReminder = postJson("/api/reminders", rejectedRequest, creator.token(), 200);
        postJson("/api/reminders/" + rejectedReminder.get("id").asLong() + "/reject", Map.of(), recipient.token(), 200);
        mvc.perform(get("/api/files/{id}/content", rejectedImageId).header("Authorization", bearer(recipient.token())))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/reminders/{id}", reminderId).header("Authorization", bearer(recipient.token())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/reminders/{id}", reminderId).header("Authorization", bearer(creator.token())))
                .andExpect(status().isOk());
    }

    @Test
    void directFriendAndRelationshipRemindersRequireActiveMembership() throws Exception {
        Account first = register("remind_relation_a", "关系甲");
        Account second = register("remind_relation_b", "关系乙");
        Account outsider = register("remind_relation_x", "关系外用户");
        makeFriends(first, second);

        JsonNode directlyAccepted = postJson("/api/reminders",
                assignedRequest("直接生效的任务", first, second), first.token(), 200);
        assertEquals("ACCEPTED", participant(directlyAccepted, second.id()).get("acceptance_status").asText());

        JsonNode invitation = postJson("/api/relationships/invitations", Map.of(
                "receiverId", second.id(), "relationshipType", "FRIEND", "message", "关系提醒测试"), first.token(), 200);
        JsonNode relationship = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/accept",
                Map.of(), second.token(), 200);
        long relationshipId = relationship.get("relationshipId").asLong();

        Map<String, Object> sharedRequest = reminderRequest("共同纪念日", LocalDateTime.now().plusDays(4));
        sharedRequest.put("relationshipId", relationshipId);
        sharedRequest.put("reminderKind", "ANNIVERSARY");
        sharedRequest.put("scheduleType", "YEARLY");
        JsonNode shared = postJson("/api/reminders", sharedRequest, first.token(), 200);
        assertEquals("RELATIONSHIP", shared.get("scope").asText());
        assertEquals(2, shared.get("participant_count").asInt());
        assertEquals("ACCEPTED", participant(shared, second.id()).get("acceptance_status").asText());

        postJson("/api/reminders", sharedRequest, outsider.token(), 403);
    }

    @Test
    void dueScanCreatesExactlyOneDeliveryAndNotificationThenAdvancesRecurrence() throws Exception {
        Account owner = register("reminder_delivery_owner", "投递用户");
        LocalDateTime due = LocalDateTime.now().minusMinutes(1).withNano(0);
        Map<String, Object> request = reminderRequest("每天喝水", due);
        request.put("scheduleType", "DAILY");
        JsonNode reminder = postJson("/api/reminders", request, owner.token(), 200);
        long reminderId = reminder.get("id").asLong();

        LocalDateTime scanTime = LocalDateTime.now().withNano(0);
        assertEquals(1, scheduler.scanDue(scanTime));
        assertEquals(0, scheduler.scanDue(scanTime));
        assertEquals(1, count("SELECT COUNT(*) FROM reminder_delivery WHERE reminder_id=? AND user_id=?", reminderId, owner.id()));
        assertEquals(1, count("SELECT COUNT(*) FROM notification WHERE user_id=? AND notification_type='REMINDER_DUE' AND reference_id=?",
                owner.id(), reminderId));

        LocalDateTime next = jdbc.queryForObject("SELECT next_trigger_at FROM reminder WHERE id=?", LocalDateTime.class, reminderId);
        assertTrue(next != null && next.isAfter(scanTime));
    }

    private Map<String, Object> assignedRequest(String title, Account creator, Account recipient) {
        Map<String, Object> request = reminderRequest(title, LocalDateTime.now().plusDays(1));
        request.put("recipientUserId", recipient.id());
        return request;
    }

    private Map<String, Object> reminderRequest(String title, LocalDateTime remindAt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("title", title);
        request.put("note", "集成测试提醒");
        request.put("reminderKind", "TASK");
        request.put("scheduleType", "ONCE");
        request.put("remindAt", remindAt.withNano(0));
        request.put("timezone", "Asia/Shanghai");
        return request;
    }

    private Account register(String username, String nickname) throws Exception {
        JsonNode response = postJson("/api/auth/register",
                Map.of("username", username, "password", "Memo123!", "nickname", nickname), null, 200);
        return new Account(response.at("/user/id").asLong(), response.get("token").asText());
    }

    private void makeFriends(Account sender, Account receiver) throws Exception {
        JsonNode request = postJson("/api/friends/requests",
                Map.of("receiverId", receiver.id(), "message", "提醒测试好友"), sender.token(), 200);
        postJson("/api/friends/requests/" + request.get("id").asLong() + "/accept",
                Map.of(), receiver.token(), 200);
    }

    private long upload(String token, String filename) throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", filename, "application/octet-stream", PNG);
        String result = mvc.perform(multipart("/api/files").file(upload).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private void expectPng(String token, long fileId) throws Exception {
        mvc.perform(get("/api/files/{id}/content", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
    }

    private JsonNode participant(JsonNode reminder, long userId) {
        for (JsonNode participant : reminder.get("participants")) {
            if (participant.get("user_id").asLong() == userId) return participant;
        }
        throw new AssertionError("Missing participant " + userId);
    }

    private JsonNode getJson(String path, String token, int expectedStatus) throws Exception {
        String result = mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode postJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode putJson(String path, Object body, String token, int expectedStatus) throws Exception {
        String result = mvc.perform(put(path).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Account(long id, String token) {}
}
