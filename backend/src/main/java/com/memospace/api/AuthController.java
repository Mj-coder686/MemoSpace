package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.AuthService;
import com.memospace.service.AppearanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final AppearanceService appearance;

    public AuthController(AuthService auth, AppearanceService appearance) {
        this.auth = auth;
        this.appearance = appearance;
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.password(), request.nickname());
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @GetMapping("/users/me")
    public Map<String, Object> me() { return auth.profile(CurrentUser.id()); }

    @PutMapping("/users/me")
    public Map<String, Object> update(@Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(CurrentUser.id(), request.nickname(), request.bio(), request.avatar(),
                request.gender(), request.birthday(), request.location());
    }

    @PutMapping("/users/me/avatar")
    public Map<String, Object> avatar(@Valid @RequestBody AvatarRequest request) {
        return auth.updateAvatar(CurrentUser.id(), request.fileId());
    }

    @GetMapping("/users/me/appearance")
    public Map<String, Object> appearance() { return appearance.get(CurrentUser.id()); }

    @PutMapping("/users/me/appearance")
    public Map<String, Object> appearance(@Valid @RequestBody AppearanceRequest request) {
        return appearance.update(CurrentUser.id(), request.backgroundColor(), request.backgroundFileId(),
                request.backgroundBrightness(), request.backgroundOverlay(), request.clearBackgroundImage());
    }

    @PutMapping("/users/me/password")
    public Map<String, String> password(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(CurrentUser.id(), request.oldPassword(), request.newPassword());
        return Map.of("message", "密码已更新");
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,24}$", message = "用户名需为 3-24 位字母、数字或下划线") String username,
            @NotBlank @Size(min = 8, max = 72, message = "密码需为 8-72 位") String password,
            @NotBlank @Size(max = 60) String nickname) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record UpdateProfileRequest(@Size(max = 60) String nickname, @Size(max = 300) String bio,
                                       @Size(max = 500) String avatar, String gender, LocalDate birthday,
                                       @Size(max = 120) String location) {}

    public record AvatarRequest(@jakarta.validation.constraints.Positive long fileId) {}
    public record AppearanceRequest(@Pattern(regexp = "^#[0-9a-fA-F]{6}$") String backgroundColor,
                                    @jakarta.validation.constraints.Positive Long backgroundFileId,
                                    Integer backgroundBrightness, Integer backgroundOverlay,
                                    boolean clearBackgroundImage) {}

    public record ChangePasswordRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
