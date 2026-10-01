package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.MobilePushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class MobilePushController {
    private final MobilePushService push;

    public MobilePushController(MobilePushService push) { this.push = push; }

    @GetMapping("/status")
    public Map<String, Object> status() { return push.status(CurrentUser.id()); }

    @PostMapping("/devices")
    public Map<String, Object> register(@Valid @RequestBody DeviceRequest request) {
        return push.register(CurrentUser.id(), request.token(), request.platform(), request.deviceName());
    }

    @DeleteMapping("/devices")
    public void unregister(@Valid @RequestBody RemoveDeviceRequest request) {
        push.unregister(CurrentUser.id(), request.token());
    }

    public record DeviceRequest(@NotBlank @Size(max = 512) String token, String platform, @Size(max = 120) String deviceName) {}
    public record RemoveDeviceRequest(@NotBlank @Size(max = 512) String token) {}
}
