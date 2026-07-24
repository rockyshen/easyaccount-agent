package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

@Data
public class CreateAccountRequestDto {
    private String name;
    private String initialMoney;
    private String card;
    private String note;
    /** 0=普通，1=信用卡 */
    private Integer accountType;
}
