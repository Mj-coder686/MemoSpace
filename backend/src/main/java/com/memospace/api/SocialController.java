package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.SocialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SocialController {
    private final SocialService social;

    public SocialController(SocialService social) { this.social = social; }

    @PostMapping("/memories/{id}/comments")
    public Map<String, Object> comment(@PathVariable long id, @Valid @RequestBody TextRequest request) {
        return social.comment(CurrentUser.id(), id, request.content());
    }

    @DeleteMapping("/comments/{id}")
    public Map<String, String> deleteComment(@PathVariable long id) {
        social.deleteComment(CurrentUser.id(), id);
        return Map.of("message", "评论已删除");
    }

    @PostMapping("/memories/{id}/reactions")
    public Map<String, Object> reaction(@PathVariable long id, @Valid @RequestBody ReactionRequest request) {
        return social.react(CurrentUser.id(), id, request.reaction());
    }

    @PostMapping("/memories/{id}/favorite")
    public Map<String, Object> favorite(@PathVariable long id) {
        return Map.of("favorite", social.favorite(CurrentUser.id(), id));
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications() { return social.notifications(CurrentUser.id()); }

    @PutMapping("/notifications/read")
    public Map<String, String> read() {
        social.readNotifications(CurrentUser.id());
        return Map.of("message", "通知已读");
    }

    @GetMapping("/spaces/{id}/messages")
    public List<Map<String, Object>> wall(@PathVariable long id) { return social.wall(CurrentUser.id(), id); }

    @PostMapping("/spaces/{id}/messages")
    public Map<String, Object> message(@PathVariable long id, @Valid @RequestBody TextRequest request) {
        return social.leaveMessage(CurrentUser.id(), id, request.content());
    }

    @PostMapping("/spaces/{id}/anniversaries")
    public Map<String, Object> anniversary(@PathVariable long id, @Valid @RequestBody AnniversaryRequest request) {
        return social.anniversary(CurrentUser.id(), id, request.title(), request.date(), request.repeatYearly());
    }

    @PutMapping("/spaces/{spaceId}/anniversaries/{anniversaryId}")
    public Map<String, Object> updateAnniversary(@PathVariable long spaceId, @PathVariable long anniversaryId,
                                                  @Valid @RequestBody AnniversaryRequest request) {
        return social.updateAnniversary(CurrentUser.id(), spaceId, anniversaryId,
                request.title(), request.date(), request.repeatYearly());
    }

    @DeleteMapping("/spaces/{spaceId}/anniversaries/{anniversaryId}")
    public Map<String, String> deleteAnniversary(@PathVariable long spaceId, @PathVariable long anniversaryId) {
        social.deleteAnniversary(CurrentUser.id(), spaceId, anniversaryId);
        return Map.of("message", "纪念日已删除");
    }

    public record TextRequest(@NotBlank @Size(max = 500) String content) {}
    public record ReactionRequest(@NotBlank String reaction) {}
    public record AnniversaryRequest(@NotBlank @Size(max = 100) String title, @NotNull LocalDate date, boolean repeatYearly) {}
}
