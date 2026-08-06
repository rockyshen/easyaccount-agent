package com.rockyshen.easyaccountagent.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TypeTemplate {
    private int id;
    private String tName;
    private Integer parent = -1;
    /** 0收入 1支出 2转账，避免绑定自增 action.id */
    private int actionHandle;
    private int sortOrder;
}
