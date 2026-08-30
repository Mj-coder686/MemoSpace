package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
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

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72, message = "临时密码需为 8-72 位") String newPassword) {}
    public record MemoIdRequest(@NotBlank @Pattern(regexp = "^\\d{12}$", message = "Memo ID 必须是 12 位纯数字") String memoId) {}
}
