package com.rockyshen.easyaccountagent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 账单图片 VL 解析的结构化结果。本期不做落库与账户匹配，仅产出候选流水。
 */
@Data
public class BillParseResultDto {

    private List<BillParseItemDto> items = new ArrayList<>();

    /** 请求时的图片 MIME，如 image/jpeg */
    private String sourceMimeType;

    /** 实际调用的 VL 模型名 */
    private String model;

    /** 模型原始文本（便于排查；后续对接层可选择不暴露） */
    private String rawModelText;
}
