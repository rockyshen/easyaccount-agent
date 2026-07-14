-- 用户名唯一，避免重复注册
ALTER TABLE `user`
  ADD UNIQUE KEY uk_user_name (name);
