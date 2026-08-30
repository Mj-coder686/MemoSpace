package com.memospace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/** Ensures an administrator token can never be reused to browse user-facing content APIs. */
@Component
public class AdminSessionBoundaryInterceptor implements HandlerInterceptor {
    private final ObjectMapper json;

    public AdminSessionBoundaryInterceptor(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return true;
        boolean adminSession = authentication.getAuthorities().stream()
                .anyMatch(authority -> JwtService.ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (!adminSession) return true;
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), Map.of(
                "status", HttpServletResponse.SC_FORBIDDEN,
                "message", "管理员会话不能访问用户 Memory、空间、聊天或媒体内容"));
        return false;
    }
}
