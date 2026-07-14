-- 为账户表增加类型字段：0=普通/储蓄，1=信用卡
-- 信用卡语义：money=可用额度，exempt_money=信用额度；净资产贡献 = money - exempt_money = -已用额度
ALTER TABLE account
    ADD COLUMN account_type TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1信用卡' AFTER note;
