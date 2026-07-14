package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.auth.AuthException;
import com.rockyshen.easyaccountagent.auth.AuthService;
import com.rockyshen.easyaccountagent.auth.AuthenticatedUser;
import com.rockyshen.easyaccountagent.dto.LoginRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody LoginRequestDto body,
                                      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        try {
            AuthService.LoginResult result = authService.register(body.getName(), body.getPassword(), ua);
            return ResponseEntity.ok(loginResponse(result));
        } catch (AuthException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto body,
                                   @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua) {
        if (body == null || body.getName() == null || body.getPassword() == null
                || body.getPassword().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "用户名或密码错误"));
        }
        try {
            AuthService.LoginResult result = authService.login(body.getName(), body.getPassword(), ua);
            return ResponseEntity.ok(loginResponse(result));
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = AuthService.extractBearerOrQueryToken(authorization, null);
        authService.logout(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = AuthService.extractBearerOrQueryToken(authorization, null);
        Optional<AuthenticatedUser> user = authService.resolveUser(token);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录或会话已失效"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.get().getId());
        body.put("name", user.get().getName());
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> loginResponse(AuthService.LoginResult result) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", result.user().getId());
        user.put("name", result.user().getName());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", result.token());
        resp.put("expiresAt", result.expiresAt().toInstant().atZone(ZoneId.systemDefault()).format(ISO));
        resp.put("user", user);
        return resp;
    }
}
