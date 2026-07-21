-- Agent 对话 checkpoint 运维示例（MysqlSaver 表：GRAPH_THREAD / GRAPH_CHECKPOINT）
-- 默认由应用 CreateOption=CREATE_IF_NOT_EXISTS 自动建表；本脚本仅供清理/备份参考。
-- thread_name 约定：u-{userId}（一用户一条会话链）

-- ========== 查看某用户会话 ==========
-- SELECT t.thread_id, t.thread_name, t.is_released,
--        COUNT(c.checkpoint_id) AS checkpoint_count,
--        MAX(c.saved_at) AS last_saved_at
-- FROM GRAPH_THREAD t
-- LEFT JOIN GRAPH_CHECKPOINT c ON c.thread_id = t.thread_id
-- WHERE t.thread_name = 'u-1'   -- 替换为实际用户
-- GROUP BY t.thread_id, t.thread_name, t.is_released;

-- ========== 清空某用户记忆（释放 thread，checkpoint 可随后清理）==========
-- UPDATE GRAPH_THREAD
-- SET is_released = TRUE
-- WHERE thread_name = 'u-1' AND is_released = FALSE;
--
-- 或物理删除（外键 CASCADE 会删掉 GRAPH_CHECKPOINT）：
-- DELETE FROM GRAPH_THREAD WHERE thread_name = 'u-1';

-- ========== TTL：删除 N 天未更新的 checkpoint（示例 30 天）==========
-- DELETE c FROM GRAPH_CHECKPOINT c
-- INNER JOIN GRAPH_THREAD t ON c.thread_id = t.thread_id
-- WHERE c.saved_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 可选：释放长期无活跃且已无 checkpoint 的 thread
-- UPDATE GRAPH_THREAD t
-- LEFT JOIN GRAPH_CHECKPOINT c ON c.thread_id = t.thread_id
-- SET t.is_released = TRUE
-- WHERE t.is_released = FALSE AND c.thread_id IS NULL;

-- ========== 库容巡检 ==========
-- SELECT
--   (SELECT COUNT(*) FROM GRAPH_THREAD WHERE is_released = FALSE) AS active_threads,
--   (SELECT COUNT(*) FROM GRAPH_CHECKPOINT) AS checkpoints,
--   (SELECT ROUND(SUM(LENGTH(state_data)) / 1024 / 1024, 2) FROM GRAPH_CHECKPOINT) AS state_mb;
