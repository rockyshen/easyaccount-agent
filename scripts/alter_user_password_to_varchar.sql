-- 将 user.password 从 INT 改为 VARCHAR，支持常规密码（大小写字母、数字、符号）
-- 原 int 值会隐式转为对应数字字符串（例如 123456 → '123456'）
ALTER TABLE `user`
  MODIFY COLUMN password VARCHAR(128) NOT NULL COMMENT '登录密码（明文字符串；哈希升级另议）';
