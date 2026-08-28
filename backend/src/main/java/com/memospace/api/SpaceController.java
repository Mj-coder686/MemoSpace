package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.SpaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {
    private final SpaceService spaces;

    public SpaceController(SpaceService spaces) { this.spaces = spaces; }

    @GetMapping
    public List<Map<String, Object>> list() { return spaces.list(CurrentUser.id()); }

    @GetMapping("/themes")
    public List<Map<String, Object>> themes() { return spaces.themes(); }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) { return spaces.detail(CurrentUser.id(), id); }

    @GetMapping("/{id}/timeline")
    public List<Map<String, Object>> timeline(@PathVariable long id) { return spaces.timeline(CurrentUser.id(), id); }

    @PutMapping("/{id}/theme")
    public Map<String, Object> theme(@PathVariable long id, @Valid @RequestBody ThemeRequest request) {
        return spaces.updateTheme(CurrentUser.id(), id, request.name(), request.themeId());
    }

    public record ThemeRequest(@Size(max = 80) String name, @Positive long themeId) {}
}
