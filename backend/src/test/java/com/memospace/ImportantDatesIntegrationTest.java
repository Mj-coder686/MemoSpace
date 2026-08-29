package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportantDatesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void relationshipAnniversaryCrudHonorsMembershipAndArchive() throws Exception {
        JsonNode alice = register("dates_alice", "纪念日甲");
        JsonNode bob = register("dates_bob", "纪念日乙");
        JsonNode outsider = register("dates_outsider", "无关用户");
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        String outsiderToken = outsider.get("token").asText();

        JsonNode categories = getJson("/api/relationship-categories", aliceToken, 200);
        long categoryId = categories.get(0).get("id").asLong();
        JsonNode invitation = postJson("/api/relationships/invitations", Map.of(
                "receiverId", bob.at("/user/id").asLong(), "categoryId", categoryId), aliceToken, 200);
        JsonNode accepted = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/accept",
                Map.of(), bobToken, 200);
        long spaceId = accepted.get("spaceId").asLong();
        long relationshipId = accepted.get("relationshipId").asLong();

        JsonNode created = postJson("/api/spaces/" + spaceId + "/anniversaries", Map.of(
                "title", "第一次见面", "date", "2024-08-26", "repeatYearly", true), aliceToken, 200);
        long anniversaryId = created.get("id").asLong();
        assertEquals("2024-08-26", created.get("anniversary_date").asText());

        putJson("/api/spaces/" + spaceId + "/anniversaries/" + anniversaryId, Map.of(
                "title", "第一次见面的下午", "date", "2024-08-27", "repeatYearly", true), bobToken, 200);
        JsonNode detail = getJson("/api/spaces/" + spaceId, aliceToken, 200);
        assertEquals("第一次见面的下午", detail.at("/anniversaries/0/title").asText());

        putJson("/api/spaces/" + spaceId + "/anniversaries/" + anniversaryId, Map.of(
                "title", "越权修改", "date", "2024-08-28", "repeatYearly", true), outsiderToken, 403);
        mvc.perform(delete("/api/spaces/{spaceId}/anniversaries/{id}", spaceId, anniversaryId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/relationships/{id}", relationshipId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());
        postJson("/api/spaces/" + spaceId + "/anniversaries", Map.of(
                "title", "封存后新增", "date", "2025-01-01", "repeatYearly", true), bobToken, 403);
        assertEquals(1, getJson("/api/spaces/" + spaceId, bobToken, 200).get("anniversaries").size());
    }

    @Test
    void calendarDayReturnsOnlyTheSignedInUsersMemoriesForThatDate() throws Exception {
        JsonNode owner = register("calendar_owner", "日历主人");
        JsonNode other = register("calendar_other", "另一位用户");
        String ownerToken = owner.get("token").asText();
        String otherToken = other.get("token").asText();
        LocalDate date = LocalDate.of(2026, 8, 28);

        postJson("/api/memories", Map.of("title", "日历中的一天", "memoryType", "TEXT",
                "occurredAt", LocalDateTime.of(2026, 8, 28, 13, 20).toString(), "visibility", "PRIVATE"), ownerToken, 200);
        postJson("/api/memories", Map.of("title", "别人的同一天", "memoryType", "TEXT",
                "occurredAt", LocalDateTime.of(2026, 8, 28, 14, 20).toString(), "visibility", "PRIVATE"), otherToken, 200);

        JsonNode day = getJson("/api/calendar/day?date=" + date, ownerToken, 200);
        assertEquals(1, day.size());
        assertEquals("日历中的一天", day.get(0).get("title").asText());
        JsonNode month = getJson("/api/calendar?year=2026&month=8", ownerToken, 200);
        assertTrue(month.toString().contains("日历中的一天"));
        assertFalse(month.toString().contains("别人的同一天"));
    }

    private JsonNode register(String username, String nickname) throws Exception {
        return postJson("/api/auth/register", Map.of("username", username, "password", "Memo123!", "nickname", nickname), null, 200);
    }

    private JsonNode postJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode putJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode getJson(String path, String token, int expectedStatus) throws Exception {
        var request = get(path).header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
