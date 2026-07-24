package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

@Data
public class UpdateAccountRequestDto {
    private String name;
    private String card;
    private String note;
    /** 普通=豁免资产；信用卡=新信用额度 */
    private String exemptMoney;
}
