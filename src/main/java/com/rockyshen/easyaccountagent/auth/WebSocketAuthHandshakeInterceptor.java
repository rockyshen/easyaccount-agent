package com.rockyshen.easyaccountagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "authenticatedUser";

    private final AuthService authService;
    private final AuthProperties authProperties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!authProperties.isEnabled()) {
            // 开发关闭鉴权时使用固定用户 id=1（需库内存在）
            attributes.put(ATTR_USER, new AuthenticatedUser(1, "dev"));
            return true;
        }

        String token = resolveToken(request);
        Optional<AuthenticatedUser> user = authService.resolveUser(token);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER, user.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String queryToken = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest raw = servletRequest.getServletRequest();
            queryToken = raw.getParameter("token");
        } else if (request.getURI().getQuery() != null) {
            for (String part : request.getURI().getQuery().split("&")) {
                if (part.startsWith("token=")) {
                    queryToken = URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        return AuthService.extractBearerOrQueryToken(authorization, queryToken);
    }
}
