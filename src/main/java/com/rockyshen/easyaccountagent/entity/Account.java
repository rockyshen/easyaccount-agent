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
}
