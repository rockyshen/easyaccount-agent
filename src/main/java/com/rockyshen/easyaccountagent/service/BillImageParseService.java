package com.rockyshen.easyaccountagent.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.MessageFormat;
import com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockyshen.easyaccountagent.config.BillParseProperties;
import com.rockyshen.easyaccountagent.constant.BillParsePrompt;
import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dto.BillParseItemDto;
import com.rockyshen.easyaccountagent.dto.BillParseResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 账单图片 → VL → 结构化候选流水。不写库、不匹配账户/分类 ID（后续工具与确认流程再接）。
 */
@Slf4j
@Service
public class BillImageParseService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");
    private static final Pattern FIRST_OBJECT = Pattern.compile("(?s)\\{.*}");

    private final ChatModel qwenVlChatModel;
    private final ObjectMapper objectMapper;
    private final BillParseProperties properties;

    public BillImageParseService(
            @Qualifier("qwenVlChatModel") ChatModel qwenVlChatModel,
            ObjectMapper objectMapper,
            BillParseProperties properties) {
        this.qwenVlChatModel = qwenVlChatModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 解析一张账单图片。
     *
     * @param imageBytes  图片字节
     * @param contentType MIME，如 image/jpeg；可为空则按 jpeg 尝试
     */
    public BillParseResultDto parseImage(byte[] imageBytes, String contentType) {
        validateImage(imageBytes, contentType);
        String mime = normalizeMime(contentType);
        String modelName = properties.getVlModel();

        String raw = callVl(imageBytes, mime, modelName);
        BillParseResultDto result = new BillParseResultDto();
        result.setSourceMimeType(mime);
        result.setModel(modelName);
        result.setRawModelText(raw);
        result.setItems(parseAndNormalizeItems(raw));
        return result;
    }

    private void validateImage(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        if (imageBytes.length > properties.getMaxBytes()) {
            throw new IllegalArgumentException(
                    "图片过大，最大允许 " + properties.getMaxBytes() + " 字节");
        }
        String mime = normalizeMime(contentType);
        if (!ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException("仅支持图片类型 jpeg/png/webp/gif，收到：" + contentType);
        }
    }

    private String normalizeMime(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "image/jpeg";
        }
        String mime = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');
        if (semi >= 0) {
            mime = mime.substring(0, semi).trim();
        }
        if ("image/jpg".equals(mime)) {
            return "image/jpeg";
        }
        return mime;
    }

    private String callVl(byte[] imageBytes, String mime, String modelName) {
        MimeType mimeType = MimeTypeUtils.parseMimeType(mime);
        Media media = new Media(mimeType, new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "bill" + extensionFor(mime);
            }
        });

        UserMessage userMessage = UserMessage.builder()
                .text(BillParsePrompt.TEXT)
                .media(media)
                .metadata(Map.of(DashScopeApiConstants.MESSAGE_FORMAT, MessageFormat.IMAGE))
                .build();

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName)
                .withMultiModel(true)
                .withTemperature(0.1)
                .build();

        ChatResponse response = qwenVlChatModel.call(new Prompt(userMessage, options));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("VL 模型无返回");
        }
        String text = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("VL 模型返回空文本");
        }
        return text.trim();
    }

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    List<BillParseItemDto> parseAndNormalizeItems(String rawModelText) {
        String json = extractJsonObject(rawModelText);
        try {
            JsonNode root = objectMapper.readTree(json);
            List<BillParseItemDto> items = new ArrayList<>();
            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    BillParseItemDto item = normalizeItem(objectMapper.treeToValue(node, BillParseItemDto.class));
                    if (item != null) {
                        items.add(item);
                    }
                }
            } else if (looksLikeItem(root)) {
                BillParseItemDto item = normalizeItem(objectMapper.treeToValue(root, BillParseItemDto.class));
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("bill_parse_json_failed raw={}", abbreviate(rawModelText), e);
            throw new IllegalStateException("无法解析 VL 返回的 JSON：" + e.getMessage(), e);
        }
    }

    private static boolean looksLikeItem(JsonNode root) {
        return root != null && root.isObject()
                && (root.has("money") || root.has("handle") || root.has("merchant"));
    }

    private static String extractJsonObject(String raw) {
        Matcher fence = JSON_FENCE.matcher(raw);
        if (fence.find()) {
            return fence.group(1);
        }
        Matcher obj = FIRST_OBJECT.matcher(raw);
        if (obj.find()) {
            return obj.group();
        }
        return raw;
    }

    private BillParseItemDto normalizeItem(BillParseItemDto raw) {
        if (raw == null) {
            return null;
        }
        String money = normalizeMoney(raw.getMoney());
        if (money == null) {
            log.warn("bill_parse_skip_item_no_money merchant={}", raw.getMerchant());
            return null;
        }
        BillParseItemDto item = new BillParseItemDto();
        item.setMoney(money);
        item.setHandle(normalizeHandle(raw.getHandle()));
        item.setDate(normalizeDate(raw.getDate()));
        item.setMerchant(trimToEmpty(raw.getMerchant()));
        item.setAccountNameHint(trimToEmpty(raw.getAccountNameHint()));
        item.setAccountToNameHint(trimToEmpty(raw.getAccountToNameHint()));
        item.setTypeNameHint(trimToEmpty(raw.getTypeNameHint()));
        item.setNote(trimToEmpty(raw.getNote()));
        item.setConfidence(normalizeConfidence(raw.getConfidence()));
        return item;
    }

    private static Integer normalizeHandle(Integer handle) {
        if (handle == null) {
            return ContentValues.ACTION_SUB;
        }
        if (handle == ContentValues.ACTION_ADD
                || handle == ContentValues.ACTION_SUB
                || handle == ContentValues.ACTION_INNER) {
            return handle;
        }
        return ContentValues.ACTION_SUB;
    }

    private static String normalizeMoney(String money) {
        if (!StringUtils.hasText(money)) {
            return null;
        }
        String cleaned = money.trim()
                .replace("¥", "")
                .replace("￥", "")
                .replace(",", "")
                .replace("元", "")
                .trim();
        try {
            BigDecimal value = new BigDecimal(cleaned).abs().setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return value.toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeDate(String date) {
        if (!StringUtils.hasText(date)) {
            return "";
        }
        String trimmed = date.trim()
                .replace('年', '-')
                .replace('月', '-')
                .replace("日", "")
                .replace('/', '-');
        try {
            return LocalDate.parse(trimmed, DATE_FMT).format(DATE_FMT);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        String[] parts = trimmed.split("-");
        if (parts.length == 3) {
            try {
                int y = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                int d = Integer.parseInt(parts[2].trim());
                return LocalDate.of(y, m, d).format(DATE_FMT);
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }

    private static Double normalizeConfidence(Double confidence) {
        if (confidence == null) {
            return null;
        }
        if (confidence < 0) {
            return 0.0;
        }
        if (confidence > 1) {
            return 1.0;
        }
        return confidence;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
