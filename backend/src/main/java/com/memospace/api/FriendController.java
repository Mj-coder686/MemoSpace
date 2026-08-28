package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.FriendService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return friends.list(CurrentUser.id());
    }

    @GetMapping("/requests")
    public List<Map<String, Object>> requests() {
        return friends.requests(CurrentUser.id());
    }

    @PostMapping("/requests")
    public Map<String, Object> request(@Valid @RequestBody FriendRequest request) {
        return friends.request(CurrentUser.id(), request.receiverId(), request.message());
    }

    @PostMapping("/requests/{id}/accept")
    public Map<String, Object> accept(@PathVariable long id) {
        return friends.respond(CurrentUser.id(), id, true);
    }

    @PostMapping("/requests/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id) {
        return friends.respond(CurrentUser.id(), id, false);
    }

    @GetMapping("/{friendId}/settings")
    public Map<String, Object> settings(@PathVariable long friendId) {
        return friends.getSettings(CurrentUser.id(), friendId);
    }

    @PutMapping("/{friendId}/settings")
    public Map<String, Object> updateSettings(@PathVariable long friendId,
                                               @Valid @RequestBody SettingsRequest request) {
        return friends.updateSettings(CurrentUser.id(), friendId, request.remarkName(),
                request.allowDirectReminders(), request.muteChat());
    }

    @DeleteMapping("/{friendId}")
    public Map<String, String> remove(@PathVariable long friendId) {
        friends.remove(CurrentUser.id(), friendId);
        return Map.of("message", "好友已移除，历史聊天仍会保留");
    }

    public record FriendRequest(@Positive long receiverId, @Size(max = 200) String message) {}

    public record SettingsRequest(@Size(max = 60) String remarkName, Boolean allowDirectReminders,
                                  Boolean muteChat) {}
}
