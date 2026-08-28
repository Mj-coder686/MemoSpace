package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.RelationshipCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/relationship-categories")
public class RelationshipCategoryController {
    private final RelationshipCategoryService categories;

    public RelationshipCategoryController(RelationshipCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean includeHidden) {
        return categories.list(CurrentUser.id(), includeHidden);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        return categories.detail(CurrentUser.id(), id);
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateCategoryRequest request) {
        return categories.createCustom(CurrentUser.id(), request.name(), request.icon(), request.themeId());
    }

    @PutMapping("/{id}/visibility")
    public Map<String, Object> visibility(@PathVariable long id, @RequestBody VisibilityRequest request) {
        return categories.setVisible(CurrentUser.id(), id, request.visible());
    }

    @PutMapping("/reorder")
    public List<Map<String, Object>> reorder(@Valid @RequestBody ReorderRequest request) {
        return categories.reorder(CurrentUser.id(), request.categoryIds());
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 40) String name,
                                        @Size(max = 32) String icon,
                                        Long themeId) {}
    public record VisibilityRequest(boolean visible) {}
    public record ReorderRequest(@NotEmpty List<Long> categoryIds) {}
}
