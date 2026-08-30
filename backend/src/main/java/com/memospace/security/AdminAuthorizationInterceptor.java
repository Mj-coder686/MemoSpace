package com.memospace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/** Performs a fresh database role check for every administrator API request. */
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AdminAuthorizationInterceptor(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof Long userId)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录管理员账号");
            return false;
        }
        boolean adminSession = authentication.getAuthorities().stream()
                .anyMatch(authority -> JwtService.ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (!adminSession) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "请使用独立的管理员登录入口");
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE id=? AND is_admin=TRUE", Integer.class, userId);
        if (count == null || count == 0) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "只有管理员可以访问此功能");
            return false;
        }
        return true;
    }

    private void reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), Map.of("status", status, "message", message));
    }
}
