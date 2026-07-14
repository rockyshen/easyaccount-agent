package com.rockyshen.easyaccountagent.auth;

import com.rockyshen.easyaccountagent.dao.AuthTokenDao;
import com.rockyshen.easyaccountagent.dao.UserDao;
import com.rockyshen.easyaccountagent.entity.AuthToken;
import com.rockyshen.easyaccountagent.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserDao userDao;
    private final AuthTokenDao authTokenDao;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public record LoginResult(String token, Date expiresAt, AuthenticatedUser user) {
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResult login(String name, String password, String userAgent) {
        if (name == null || name.isBlank() || password == null || password.isEmpty()) {
            throw new AuthException(401, "用户名或密码错误");
        }
        User user = userDao.findByNameAndPassword(name.trim(), password);
        if (user == null) {
            throw new AuthException(401, "用户名或密码错误");
        }
        // 单端登录：踢掉该用户全部旧会话
        authTokenDao.revokeAllByUserId(user.getId());

        String rawToken = generateRawToken();
        Date now = new Date();
        Date expiresAt = Date.from(Instant.now().plus(authProperties.getTokenTtlDays(), ChronoUnit.DAYS));

        AuthToken row = new AuthToken();
        row.setUserId(user.getId());
        row.setTokenHash(sha256Hex(rawToken));
        row.setCreatedAt(now);
        row.setLastUsedAt(now);
        row.setExpiresAt(expiresAt);
        row.setRevoked(false);
        row.setUserAgent(truncate(userAgent, 255));
        authTokenDao.insert(row);

        return new LoginResult(rawToken, expiresAt, new AuthenticatedUser(user.getId(), user.getName()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        authTokenDao.revokeByHash(sha256Hex(rawToken));
    }

    /**
     * 校验 token；可选滑动续期。无效返回 empty。
     */
    @Transactional(rollbackFor = Exception.class)
    public Optional<AuthenticatedUser> resolveUser(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        AuthToken row = authTokenDao.findActiveByHash(sha256Hex(rawToken));
        if (row == null) {
            return Optional.empty();
        }
        Date now = new Date();
        if (row.getExpiresAt() == null || !row.getExpiresAt().after(now)) {
            authTokenDao.revokeByHash(row.getTokenHash());
            return Optional.empty();
        }
        User user = userDao.findById(row.getUserId());
        if (user == null) {
            authTokenDao.revokeByHash(row.getTokenHash());
            return Optional.empty();
        }

        Date newExpires = row.getExpiresAt();
        long renewDays = authProperties.getSlidingRenewDays();
        Instant renewThreshold = Instant.now().plus(renewDays, ChronoUnit.DAYS);
        if (row.getExpiresAt().toInstant().isBefore(renewThreshold)) {
            newExpires = Date.from(Instant.now().plus(authProperties.getTokenTtlDays(), ChronoUnit.DAYS));
        }
        authTokenDao.touch(row.getId(), now, newExpires);
        return Optional.of(new AuthenticatedUser(user.getId(), user.getName()));
    }

    public static String extractBearerOrQueryToken(String authorizationHeader, String queryToken) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            String h = authorizationHeader.trim();
            if (h.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return h.substring(7).trim();
            }
        }
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
