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

/**
 * 系统提示词从 classpath 加载，便于单独改文案：
 * {@code src/main/resources/prompts/easyaccounts-system.txt}
 * {@code src/main/resources/prompts/easyaccounts-date-context.txt}
 */
public final class EasyAccountsPrompt {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SYSTEM_RESOURCE = "/prompts/easyaccounts-system.txt";
    private static final String DATE_CONTEXT_RESOURCE = "/prompts/easyaccounts-date-context.txt";

    public static final String TEXT = loadResource(SYSTEM_RESOURCE);

    private static final String DATE_CONTEXT_TEMPLATE = loadResource(DATE_CONTEXT_RESOURCE);

    /** 供每次模型调用注入；时区与数据源一致（Asia/Shanghai）。 */
    public static String currentDateContext() {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return DATE_CONTEXT_TEMPLATE
                .replace("{date}", now.toLocalDate().format(DATE_FMT))
                .replace("{weekday}", weekday);
    }

    private static String loadResource(String path) {
        try (InputStream in = EasyAccountsPrompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("缺少提示词资源: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + path, e);
        }
    }

    private EasyAccountsPrompt() {
    }
}
