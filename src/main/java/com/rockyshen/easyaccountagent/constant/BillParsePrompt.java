package com.rockyshen.easyaccountagent.constant;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** 账单图片 VL 解析提示词：{@code /prompts/bill-image-parse.txt} */
public final class BillParsePrompt {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String RESOURCE = "/prompts/bill-image-parse.txt";
    private static final String DATE_CONTEXT_RESOURCE = "/prompts/bill-image-date-context.txt";

    public static final String TEXT = loadResource(RESOURCE);

    private static final String DATE_CONTEXT_TEMPLATE = loadResource(DATE_CONTEXT_RESOURCE);

    /** 供每次 VL 调用注入；时区与业务一致（Asia/Shanghai）。 */
    public static String currentDateContext() {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return DATE_CONTEXT_TEMPLATE
                .replace("{date}", now.toLocalDate().format(DATE_FMT))
                .replace("{weekday}", weekday);
    }

    /** 静态规则 + 本次当前日期，供 VL UserMessage 使用。 */
    public static String textWithCurrentDate() {
        return TEXT + "\n\n" + currentDateContext();
    }

    private static String loadResource(String path) {
        try (InputStream in = BillParsePrompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("缺少提示词资源: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + path, e);
        }
    }

    private BillParsePrompt() {
    }
}
