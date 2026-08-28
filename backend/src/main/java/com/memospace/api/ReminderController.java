package com.memospace.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.memospace.security.CurrentUser;
import com.memospace.service.ReminderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return reminders.list(CurrentUser.id());
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ReminderRequest request) {
        return reminders.create(CurrentUser.id(), request.title(), request.note(), request.reminderKind(),
                request.scheduleType(), request.remindAt(), request.timezone(), request.imageFileId(),
                request.relatedUserId(), request.recipientUserId(), request.relationshipId());
    }

    @PostMapping("/{id}/accept")
    public Map<String, Object> accept(@PathVariable long id) {
        return reminders.accept(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id) {
        return reminders.reject(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable long id) {
        return reminders.complete(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/snooze")
    public Map<String, Object> snooze(@PathVariable long id, @Valid @RequestBody SnoozeRequest request) {
        return reminders.snooze(CurrentUser.id(), id, request.remindAt());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        reminders.delete(CurrentUser.id(), id);
        return Map.of("message", "提醒已删除");
    }

    public record ReminderRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 1000) String note,
            @NotBlank String reminderKind,
            @NotBlank String scheduleType,
            @NotNull LocalDateTime remindAt,
            @Size(max = 60) String timezone,
            @Positive Long imageFileId,
            @Positive Long relatedUserId,
            @JsonAlias({"targetUserId", "participantUserId", "assigneeId"}) @Positive Long recipientUserId,
            @Positive Long relationshipId) {}

    public record SnoozeRequest(@NotNull LocalDateTime remindAt) {}
}
