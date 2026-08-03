-- 流水逻辑删除：f_disable=1 表示已删除，列表/汇总不再计入
-- 若列已存在可跳过
ALTER TABLE flow
    ADD COLUMN f_disable TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1已逻辑删除' AFTER collect;
