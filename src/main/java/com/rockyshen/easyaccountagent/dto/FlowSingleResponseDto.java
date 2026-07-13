package com.rockyshen.easyaccountagent.dto;

import com.rockyshen.easyaccountagent.entity.Account;
import com.rockyshen.easyaccountagent.entity.Action;
import lombok.Data;

@Data
public class FlowSingleResponseDto {
    private int id;
    private String money;
    private String fDate;
    private Action action;
    private Account account;
    private Account accountTo;
    private TypeListResponseDto type;
    private boolean isCollect;
    private String note;

}
