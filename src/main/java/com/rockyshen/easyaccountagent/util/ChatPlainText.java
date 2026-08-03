package com.rockyshen.easyaccountagent.util;

import java.util.regex.Pattern;

/**
 * 将模型输出的常见 Markdown 标记转为 iOS 纯文本气泡可直接显示的内容。
 * 面向流式小片段：剥离后若只剩空白，调用方应丢弃该 delta。
 */
public final class ChatPlainText {

    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern CODE_FENCE_LINE = Pattern.compile("(?m)^```\\w*\\s*");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]+\\)");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    private ChatPlainText() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String s = text;
        s = CODE_FENCE_LINE.matcher(s).replaceAll("");
        s = s.replace("```", "");
        s = HEADING.matcher(s).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = s.replace("**", "").replace("__", "");
        s = s.replace("`", "");
        // 单独残留的标题井号（流式拆包常见）
        if (s.chars().allMatch(c -> c == '#' || Character.isWhitespace(c))) {
            return "";
        }
        s = MULTI_NEWLINE.matcher(s).replaceAll("\n\n");
        return s;
    }
}
