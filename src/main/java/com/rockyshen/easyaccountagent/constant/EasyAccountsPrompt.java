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
import java.util.regex.Pattern;

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
    /** 匹配已注入的「当前日期：…」整段，避免 ReAct 多步或 checkpoint 复用时叠出多个日期。 */
    private static final Pattern DATE_CONTEXT_BLOCK =
            Pattern.compile("(?:\\n\\n)?当前日期：[^\\n]*");

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

    /** 去掉 system 文本里已有的当前日期段，再追加本次新鲜注入。 */
    public static String mergeSystemWithDateContext(String systemText) {
        String base = stripDateContext(systemText);
        String dateContext = currentDateContext();
        if (base.isBlank()) {
            return dateContext;
        }
        return base + "\n\n" + dateContext;
    }

    static String stripDateContext(String systemText) {
        if (systemText == null || systemText.isBlank()) {
            return "";
        }
        return DATE_CONTEXT_BLOCK.matcher(systemText).replaceAll("").trim();
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
