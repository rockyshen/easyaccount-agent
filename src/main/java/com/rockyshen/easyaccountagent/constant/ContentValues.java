package com.rockyshen.easyaccountagent.constant;

public final class ContentValues {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_SUB = 1;
    public static final int ACTION_INNER = 2;

    /** 普通/储蓄账户 */
    public static final int ACCOUNT_TYPE_NORMAL = 0;
    /** 信用卡：money=可用额度，exemptMoney=信用额度 */
    public static final int ACCOUNT_TYPE_CREDIT = 1;

    private ContentValues() {
    }
}
