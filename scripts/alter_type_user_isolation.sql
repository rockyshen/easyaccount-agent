-- type 按用户隔离 + 预设分类模板
-- 执行前请备份。应用启动时 TypeUserIsolationSchemaInitializer 也会尝试自动加列/建表。

-- 1) 用户分类增加 user_id（存量全局行可为 NULL，业务查询始终按当前用户过滤）
ALTER TABLE `type`
  ADD COLUMN user_id INT NULL COMMENT '所属用户；业务分类必填' AFTER id;

CREATE INDEX idx_type_user ON `type`(user_id);
CREATE INDEX idx_type_user_action ON `type`(user_id, action_id);

-- 2) 产品预设分类模板（全局，无 user_id）
CREATE TABLE IF NOT EXISTS type_template (
  id            INT PRIMARY KEY AUTO_INCREMENT,
  t_name        VARCHAR(50)  NOT NULL,
  parent        INT          NOT NULL DEFAULT -1 COMMENT '-1 为一级；否则为模板父节点 id',
  action_handle INT          NOT NULL COMMENT '0收入 1支出 2转账',
  sort_order    INT          NOT NULL DEFAULT 0,
  KEY idx_type_template_parent (parent),
  KEY idx_type_template_handle (action_handle)
) COMMENT '注册时克隆到用户 type 表的预设分类模板';

-- 3) 可选：将现网全局 type 导出为模板（若模板表为空且仍有无 user_id 的旧数据）
-- INSERT INTO type_template (t_name, parent, action_handle, sort_order)
-- SELECT t.t_name, t.parent, a.handle, t.id
-- FROM type t
-- JOIN action a ON a.id = t.action_id
-- WHERE t.user_id IS NULL AND (t.t_disable = 0 OR t.t_disable IS NULL)
--   AND NOT EXISTS (SELECT 1 FROM type_template LIMIT 1);

-- 4) 存量用户补种与 flow.type_id remap 请在维护窗口按环境执行；
--    应用侧会对「完全没有个人 type」的用户在登录时幂等克隆模板。
