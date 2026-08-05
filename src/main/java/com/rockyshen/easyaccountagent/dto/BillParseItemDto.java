package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

/**
 * VL 从账单图片识别出的单条候选流水（尚未解析为账户/分类 ID）。
 */
@Data
public class BillParseItemDto {

    /**
     * 收支类型 handle：0=收入，1=支出，2=转账。
     * 对应 {@link com.rockyshen.easyaccountagent.constant.ContentValues}。
     */
    private Integer handle;

    /** 金额，两位小数正数字符串 */
    private String money;

    /** 业务日期 yyyy-MM-dd；识别不到则为空 */
    private String date;

    /** 商户/对方 */
    private String merchant;

    /** 账户名提示（付款账户或收入入账账户） */
    private String accountNameHint;

    /** 转账目标账户名提示 */
    private String accountToNameHint;

    /** 分类名提示 */
    private String typeNameHint;

    /** 备注 */
    private String note;

    /** 0~1，模型对该条的把握；可为空 */
    private Double confidence;
}
