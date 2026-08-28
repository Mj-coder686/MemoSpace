package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EventController {
    private final EventService events;

    public EventController(EventService events) { this.events = events; }

    @PostMapping("/spaces/{spaceId}/events")
    public Map<String, Object> create(@PathVariable long spaceId, @Valid @RequestBody EventRequest request) {
        return events.create(CurrentUser.id(), spaceId, request.name(), request.description(), request.startAt(), request.endAt(), request.location(), request.memoryIds());
    }

    @GetMapping("/spaces/{spaceId}/events")
    public List<Map<String, Object>> list(@PathVariable long spaceId) { return events.list(CurrentUser.id(), spaceId); }

    @GetMapping("/events/{id}")
    public Map<String, Object> detail(@PathVariable long id) { return events.detail(CurrentUser.id(), id); }

    public record EventRequest(@NotBlank @Size(max = 160) String name, @Size(max = 10000) String description,
                               @NotNull LocalDateTime startAt, LocalDateTime endAt, @Size(max = 160) String location,
                               List<Long> memoryIds) {}
}
