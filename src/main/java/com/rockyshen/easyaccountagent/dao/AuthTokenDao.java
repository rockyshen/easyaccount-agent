package com.rockyshen.easyaccountagent.dao;

import com.rockyshen.easyaccountagent.entity.AuthToken;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthTokenDao {

    @Insert("INSERT INTO auth_token (user_id, token_hash, expires_at, created_at, last_used_at, revoked, user_agent) "
            + "VALUES (#{userId}, #{tokenHash}, #{expiresAt}, #{createdAt}, #{lastUsedAt}, #{revoked}, #{userAgent})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuthToken token);

    @Select("SELECT id, user_id AS userId, token_hash AS tokenHash, expires_at AS expiresAt, "
            + "created_at AS createdAt, last_used_at AS lastUsedAt, revoked, user_agent AS userAgent "
            + "FROM auth_token WHERE token_hash = #{tokenHash} AND revoked = 0 LIMIT 1")
    AuthToken findActiveByHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE auth_token SET revoked = 1 WHERE user_id = #{userId} AND revoked = 0")
    int revokeAllByUserId(@Param("userId") int userId);

    @Update("UPDATE auth_token SET revoked = 1 WHERE token_hash = #{tokenHash} AND revoked = 0")
    int revokeByHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE auth_token SET last_used_at = #{lastUsedAt}, expires_at = #{expiresAt} WHERE id = #{id}")
    void touch(@Param("id") long id, @Param("lastUsedAt") java.util.Date lastUsedAt,
               @Param("expiresAt") java.util.Date expiresAt);
}
