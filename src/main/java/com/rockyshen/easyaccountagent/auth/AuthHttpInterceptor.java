package com.rockyshen.easyaccountagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 为业务 REST 注入 {@link AuthContext}（Bearer token）。
 */
@Component
@RequiredArgsConstructor
public class AuthHttpInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final AuthProperties authProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!authProperties.isEnabled()) {
            AuthContext.setUserId(1);
            return true;
        }
        String token = AuthService.extractBearerOrQueryToken(
                request.getHeader(HttpHeaders.AUTHORIZATION), null);
        Optional<AuthenticatedUser> user = authService.resolveUser(token);
        if (user.isEmpty()) {
            writeUnauthorized(response);
            return false;
        }
        AuthContext.setUserId(user.get().getId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private static void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"未登录或会话已失效\"}");
    }
}
