package com.rockyshen.easyaccountagent.constant;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 账单图片 VL 解析提示词：{@code /prompts/bill-image-parse.txt} */
public final class BillParsePrompt {

    private static final String RESOURCE = "/prompts/bill-image-parse.txt";

    public static final String TEXT = load();

    private static String load() {
        try (InputStream in = BillParsePrompt.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("缺少提示词资源: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + RESOURCE, e);
        }
    }

    private BillParsePrompt() {
    }
}
