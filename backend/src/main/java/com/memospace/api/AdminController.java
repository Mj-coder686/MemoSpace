package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.AdminService;
import com.memospace.service.FileStorageService;
import com.memospace.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final ModerationService moderation;
    private final FileStorageService files;

    public AdminController(AdminService admin, ModerationService moderation, FileStorageService files) {
        this.admin = admin;
        this.moderation = moderation;
        this.files = files;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return admin.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public Map<String, Object> me() { return admin.me(CurrentUser.id()); }

    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "") String keyword,
                                     @RequestParam(defaultValue = "1") @Min(1) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return admin.users(keyword, page, size);
    }

    @PutMapping("/users/{userId}/password")
    public Map<String, Object> resetPassword(@PathVariable long userId,
                                             @Valid @RequestBody ResetPasswordRequest request) {
        return admin.resetPassword(CurrentUser.id(), userId, request.newPassword());
    }

    @PutMapping("/users/{userId}/memo-id")
    public Map<String, Object> changeMemoId(@PathVariable long userId,
                                            @Valid @RequestBody MemoIdRequest request) {
        return admin.changeMemoId(CurrentUser.id(), userId, request.memoId());
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return admin.audit(limit);
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@RequestParam(defaultValue = "PENDING") String status,
                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return moderation.adminReports(status, page, size);
    }

    @GetMapping("/reports/{reportId}")
    public Map<String, Object> report(@PathVariable long reportId) { return moderation.adminDetail(reportId); }

    @PutMapping("/reports/{reportId}/resolve")
    public Map<String, Object> resolveReport(@PathVariable long reportId, @Valid @RequestBody ResolveReportRequest request) {
        return moderation.resolve(CurrentUser.id(), reportId, request.decision(), request.removeContent(), request.penalty(), request.note());
    }

    @PutMapping("/users/{userId}/status")
    public Map<String, Object> accountStatus(@PathVariable long userId, @Valid @RequestBody AccountStatusRequest request) {
        return moderation.setAccountStatus(CurrentUser.id(), userId, request.action(), request.note());
    }

    @GetMapping("/reports/{reportId}/media/{fileId}")
    public ResponseEntity<?> reportMedia(@PathVariable long reportId, @PathVariable long fileId) {
        FileStorageService.StoredFile file = files.loadReportEvidence(reportId, fileId);
        MediaType type;
        try { type = MediaType.parseMediaType(file.mimeType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type).contentLength(file.size())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.resource());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72, message = "临时密码需为 8-72 位") String newPassword) {}
    public record MemoIdRequest(@NotBlank @Pattern(regexp = "^\\d{12}$", message = "Memo ID 必须是 12 位纯数字") String memoId) {}
    public record ResolveReportRequest(@NotBlank String decision, boolean removeContent, @NotBlank String penalty,
                                       @Size(max = 500) String note) {}
    public record AccountStatusRequest(@NotBlank String action, @Size(max = 500) String note) {}
}
