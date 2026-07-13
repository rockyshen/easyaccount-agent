package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

@Data
public class Action {
    private int id;
    private String hName;
    private boolean exempt;
    private int handle;
}
