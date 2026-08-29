package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.MemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MemoryController {
    private final MemoryService memories;

    public MemoryController(MemoryService memories) { this.memories = memories; }

    @PostMapping("/memories")
    public Map<String, Object> create(@Valid @RequestBody MemoryRequest request) {
        return memories.create(CurrentUser.id(), new MemoryService.CreateCommand(request.title(), request.content(), request.memoryType(),
                request.occurredAt(), request.location(), request.latitude(), request.longitude(), request.visibility(),
                request.spaceIds(), request.customViewerIds(), request.fileIds()));
    }

    @GetMapping("/memories")
    public List<Map<String, Object>> list(@RequestParam(required = false) Long spaceId,
                                         @RequestParam(defaultValue = "") String q) {
        return memories.mine(CurrentUser.id(), spaceId, q);
    }

    @GetMapping("/memories/{id}")
    public Map<String, Object> detail(@PathVariable long id) { return memories.detail(CurrentUser.id(), id); }

    @PutMapping("/memories/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody UpdateMemoryRequest request) {
        return memories.update(CurrentUser.id(), id, request.title(), request.content(), request.visibility());
    }

    @DeleteMapping("/memories/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        memories.delete(CurrentUser.id(), id);
        return Map.of("message", "记忆已删除");
    }

    @GetMapping("/feed")
    public List<Map<String, Object>> feed(@RequestParam(defaultValue = "latest") String scope) {
        return memories.feed(CurrentUser.id(), scope);
    }

    @GetMapping("/home")
    public Map<String, Object> home() { return memories.home(CurrentUser.id()); }

    @GetMapping("/calendar")
    public List<Map<String, Object>> calendar(@RequestParam(defaultValue = "0") int year,
                                               @RequestParam(defaultValue = "0") int month) {
        LocalDate today = LocalDate.now();
        return memories.calendar(CurrentUser.id(), year == 0 ? today.getYear() : year, month == 0 ? today.getMonthValue() : month);
    }

    @GetMapping("/calendar/day")
    public List<Map<String, Object>> calendarDay(@RequestParam LocalDate date) {
        return memories.calendarDay(CurrentUser.id(), date);
    }

    @GetMapping("/map")
    public List<Map<String, Object>> map() { return memories.map(CurrentUser.id()); }

    public record MemoryRequest(@NotBlank @Size(max = 160) String title, @Size(max = 10000) String content,
                                @NotBlank String memoryType, LocalDateTime occurredAt, @Size(max = 160) String location,
                                Double latitude, Double longitude, @NotBlank String visibility,
                                List<Long> spaceIds, List<Long> customViewerIds, List<Long> fileIds) {}

    public record UpdateMemoryRequest(@Size(max = 160) String title, @Size(max = 10000) String content, String visibility) {}
}
