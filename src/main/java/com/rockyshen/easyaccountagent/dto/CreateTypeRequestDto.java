package com.rockyshen.easyaccountagent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateTypeRequestDto {
    /** 分类名；请求同时接受 tname / tName */
    @JsonProperty("tname")
    @JsonAlias({"tName"})
    private String tName;

    private Integer actionId;

    /** 父分类 ID；缺省 / null / 0 视为一级（-1） */
    private Integer parent;
}
