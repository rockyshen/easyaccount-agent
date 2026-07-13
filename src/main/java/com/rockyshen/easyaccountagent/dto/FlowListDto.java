package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

import java.util.List;

@Data
public class FlowListDto {
    private String totalIn;
    private String totalOut;
    private String totalEarn;
    private List<FlowListSingleDto> flows;

    @Data
    public static class FlowListSingleDto {
        private int id;
        private String tName;
        private String money;
        private boolean exempt;
        private boolean collect;
        private int handle;
        private String hName;
        private String note;
        private String aName;
        private String toAName;
        private String fDate;
    }
}
