package com.memospace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memospace.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AccountStatusInterceptor implements HandlerInterceptor {
    private final ModerationService moderation;
    private final ObjectMapper json;

    public AccountStatusInterceptor(ModerationService moderation, ObjectMapper json) {
        this.moderation = moderation;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof Long userId) || !moderation.isBanned(userId)) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), Map.of("status", 403, "message", "账号已被封禁，请联系管理员"));
        return false;
    }
}
