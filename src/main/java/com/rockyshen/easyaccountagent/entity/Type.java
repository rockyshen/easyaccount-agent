package com.rockyshen.easyaccountagent.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Type {
    private int id;
    private String tName;
    private Integer parent = -1;
    private boolean disable = false;
    private Boolean hasChild = false;
    private Boolean archive = false;
    private Integer actionId;
    private Boolean analysisDisable = false;
}
