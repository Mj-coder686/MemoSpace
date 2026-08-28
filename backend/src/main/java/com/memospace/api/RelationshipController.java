package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.RelationshipCategoryService;
import com.memospace.service.RelationshipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/relationships")
public class RelationshipController {
    private final RelationshipService relationships;
    private final RelationshipCategoryService categories;

    public RelationshipController(RelationshipService relationships, RelationshipCategoryService categories) {
        this.relationships = relationships;
        this.categories = categories;
    }

    @GetMapping
    public List<Map<String, Object>> list() { return relationships.list(CurrentUser.id()); }

    @GetMapping("/invitations")
    public List<Map<String, Object>> invitations() { return relationships.invitations(CurrentUser.id()); }

    @PostMapping("/invitations")
    public Map<String, Object> invite(@Valid @RequestBody InviteRequest request) {
        long userId = CurrentUser.id();
        Long categoryId = request.categoryId();
        if (categoryId == null) {
            categoryId = categories.defaultCategoryId(userId,
                    request.relationshipType() == null ? "FRIEND" : request.relationshipType());
        }
        return relationships.invite(userId, request.receiverId(), categoryId, request.message());
    }

    @PostMapping("/invitations/{id}/accept")
    public Map<String, Object> accept(@PathVariable long id) { return relationships.respond(CurrentUser.id(), id, true); }

    @PostMapping("/invitations/{id}/reject")
    public Map<String, Object> reject(@PathVariable long id) { return relationships.respond(CurrentUser.id(), id, false); }

    @DeleteMapping("/{id}")
    public Map<String, String> archive(@PathVariable long id) {
        relationships.archive(CurrentUser.id(), id);
        return Map.of("message", "关系空间已封存，历史记忆仍可查看");
    }

    @PutMapping("/{id}/categories")
    public Map<String, Object> replaceCategories(@PathVariable long id,
                                                  @Valid @RequestBody CategoriesRequest request) {
        return Map.of("categories", relationships.replaceCategories(CurrentUser.id(), id, request.categoryIds()));
    }

    public record InviteRequest(@Positive long receiverId, @Positive Long categoryId, String relationshipType,
                                @Size(max = 200) String message) {}

    public record CategoriesRequest(@NotEmpty List<@Positive Long> categoryIds) {}
}
