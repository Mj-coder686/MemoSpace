package com.memospace.api;

import com.memospace.security.CurrentUser;
import com.memospace.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(defaultValue = "") String q) {
        return users.search(CurrentUser.id(), q);
    }

    @GetMapping("/{id}")
    public Map<String, Object> profile(@PathVariable long id) {
        return users.profile(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/follow")
    public Map<String, Object> follow(@PathVariable long id) {
        return Map.of("following", users.toggleFollow(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/block")
    public Map<String, Object> block(@PathVariable long id) {
        return Map.of("blocked", users.toggleBlock(CurrentUser.id(), id));
    }
}
