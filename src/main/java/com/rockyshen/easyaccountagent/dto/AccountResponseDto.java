package com.rockyshen.easyaccountagent.dto;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.entity.Account;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;

@Data
public class AccountResponseDto {
    private int id;
    private String name;
    private String money;
    private String exemptMoney;
    private String card;
    private String createTime;
    private String note;
    private int accountType;
    private String typeLabel;
    /** 信用卡已用额度；普通账户为空 */
    private String usedMoney;

    public AccountResponseDto convertToDto(Account account) {
        if (account == null) {
            return this;
        }
        setId(account.getId());
        if (Boolean.TRUE.equals(account.getDisable())) {
            setName(account.getAName() + "(已停用)");
        } else {
            setName(account.getAName());
        }
        setMoney(account.getMoney());
        setExemptMoney(account.getExemptMoney());
        setCard(account.getCard());
        if (account.getCreateTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            setCreateTime(sdf.format(account.getCreateTime()));
        }
        setNote(account.getNote());
        int type = account.getAccountType() == null ? ContentValues.ACCOUNT_TYPE_NORMAL : account.getAccountType();
        setAccountType(type);
        setTypeLabel(type == ContentValues.ACCOUNT_TYPE_CREDIT ? "信用卡" : "普通");
        if (type == ContentValues.ACCOUNT_TYPE_CREDIT) {
            BigDecimal limit = new BigDecimal(nullToZero(account.getExemptMoney()));
            BigDecimal available = new BigDecimal(nullToZero(account.getMoney()));
            setUsedMoney(limit.subtract(available).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        return this;
    }

    private static String nullToZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }
}
