package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Account {
    private int id;
    private String money;
    private String exemptMoney;
    private String aName;
    private String card;
    private Boolean disable;
    private Date createTime;
    private String note;
    /** 0=普通账户，1=信用卡 */
    private Integer accountType;
}
