package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ModerationService moderation;

    public ReportController(ModerationService moderation) { this.moderation = moderation; }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody ReportRequest request) {
        return moderation.report(CurrentUser.id(), request.targetType(), request.targetId(), request.reasonCategory(), request.description());
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine() { return moderation.mine(CurrentUser.id()); }

    public record ReportRequest(@NotBlank String targetType, @Positive long targetId, @NotBlank String reasonCategory,
                                @Size(max = 500) String description) {}
}
