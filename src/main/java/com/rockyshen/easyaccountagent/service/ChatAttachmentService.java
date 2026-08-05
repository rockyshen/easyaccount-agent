package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.config.ChatAttachmentProperties;
import com.rockyshen.easyaccountagent.dao.ChatAttachmentJdbcRepository;
import com.rockyshen.easyaccountagent.dto.BillParseItemDto;
import com.rockyshen.easyaccountagent.dto.BillParseResultDto;
import com.rockyshen.easyaccountagent.dto.ChatAttachmentResponseDto;
import com.rockyshen.easyaccountagent.entity.ChatAttachment;
import com.rockyshen.easyaccountagent.storage.LocalAttachmentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif", "image/gif");
    private static final String DEFAULT_IMAGE_ONLY_PROMPT = "用户发送了账单图片，请先展示识别结果并等待确认，不要直接记账。";

    /** 附件识别当轮强制先确认、禁止写入的指令（拼进 Agent 用户消息）。 */
    static final String CONFIRM_BEFORE_WRITE_INSTRUCTION = """
            【强制指令｜附件识别本轮】
            1. 本轮严禁调用任何写入类工具：addExpense、addIncome、transferMoney、updateFlow、deleteFlow、repayCreditCard，以及 createAccount、updateAccount、deleteAccount、createType、updateType、deleteType。
            2. 本轮只做一件事：把下方识别结果整理成待确认清单发给用户，并明确请用户确认或修改；不要落库。
            3. 允许只读：listAccounts、listActions、listTypesByAction 及查询类工具，用于核对账户/分类名称建议。
            4. 用户下一条明确确认（如「确认」「记得」「好的」「没问题」）后，再按确认内容调用写入工具；若用户要改金额/日期/账户/分类，按修改后的内容记账。
            5. 金额与日期以识别结果为准（用户另有修改除外）；用户说「今天」时 explicitDate 仍传空字符串。
            6. 若未识别到可记账流水，如实告知，禁止编造，禁止调用写入工具。
            """.trim();

    private final ChatAttachmentJdbcRepository repository;
    private final LocalAttachmentStorage storage;
    private final ChatAttachmentProperties properties;
    private final BillImageParseService billImageParseService;

    public ChatAttachmentResponseDto upload(int userId, MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "请上传图片文件");
        }
        String resolvedKind = StringUtils.hasText(kind) ? kind.trim().toLowerCase(Locale.ROOT) : "image";
        if (!"image".equals(resolvedKind)) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "本期仅支持 kind=image");
        }

        String mime = normalizeMime(file.getContentType());
        if (!ALLOWED_MIME.contains(mime)) {
            throw new AttachmentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new AttachmentException(HttpStatus.PAYLOAD_TOO_LARGE, "图片过大");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "读取上传文件失败");
        }
        if (bytes.length == 0) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "请上传图片文件");
        }
        if (bytes.length > properties.getMaxBytes()) {
            throw new AttachmentException(HttpStatus.PAYLOAD_TOO_LARGE, "图片过大");
        }

        String id = "att_" + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expires = now.plus(properties.getTtlHours(), ChronoUnit.HOURS);
        Integer[] wh = readImageSize(bytes);

        String relativePath;
        try {
            relativePath = storage.write(userId, id, extensionFor(mime), bytes);
        } catch (IOException e) {
            log.error("[ChatAttachment] 写盘失败 userId={} id={}", userId, id, e);
            throw new AttachmentException(HttpStatus.INTERNAL_SERVER_ERROR, "保存附件失败");
        }

        ChatAttachment att = new ChatAttachment();
        att.setId(id);
        att.setUserId(userId);
        att.setKind(resolvedKind);
        att.setMimeType(mime.equals("image/jpg") ? "image/jpeg" : mime);
        att.setSizeBytes(bytes.length);
        att.setWidth(wh[0]);
        att.setHeight(wh[1]);
        att.setStoragePath(relativePath);
        att.setReferenced(false);
        att.setCreatedAt(Date.from(now));
        att.setExpiresAt(Date.from(expires));

        try {
            repository.insert(att);
        } catch (Exception e) {
            storage.deleteQuietly(relativePath);
            log.error("[ChatAttachment] 写库失败 userId={} id={}", userId, id, e);
            throw new AttachmentException(HttpStatus.INTERNAL_SERVER_ERROR, "保存附件失败");
        }
        return toDto(att);
    }

    public ChatAttachmentResponseDto getForUser(int userId, String id) {
        ChatAttachment att = requireOwned(userId, id);
        return toDto(att);
    }

    public void deleteForUser(int userId, String id) {
        ChatAttachment att = requireOwned(userId, id);
        if (att.isReferenced()) {
            throw new AttachmentException(HttpStatus.CONFLICT, "附件已被引用，无法删除");
        }
        repository.deleteById(id);
        storage.deleteQuietly(att.getStoragePath());
    }

    /**
     * 开聊前校验：数量、归属、未过期。
     */
    public void assertUsableForChat(int userId, List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentIds.size() > properties.getMaxCount()) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件数量超过限制");
        }
        List<String> distinct = attachmentIds.stream().distinct().toList();
        if (distinct.size() != attachmentIds.size()) {
            throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
        }
        Instant now = Instant.now();
        for (String id : attachmentIds) {
            if (!StringUtils.hasText(id)) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
            }
            ChatAttachment att = repository.findById(id.trim());
            if (att == null || att.getUserId() != userId) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
            }
            if (att.getExpiresAt() != null && att.getExpiresAt().toInstant().isBefore(now)) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
            }
            if (!"image".equalsIgnoreCase(att.getKind())) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
            }
        }
    }

    /**
     * 解析附件为结构化结果，拼进 Agent 文本输入，并标记已引用。
     */
    public String buildAgentInput(int userId, String userContent, List<String> attachmentIds) {
        String text = userContent == null ? "" : userContent.trim();
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return text;
        }

        List<String> blocks = new ArrayList<>();
        int index = 1;
        Date now = new Date();
        for (String rawId : attachmentIds) {
            String id = rawId.trim();
            ChatAttachment att = requireOwned(userId, id);
            if (att.getExpiresAt() != null && att.getExpiresAt().before(now)) {
                throw new AttachmentException(HttpStatus.BAD_REQUEST, "附件无效或已过期");
            }
            byte[] bytes;
            try {
                bytes = storage.read(att.getStoragePath());
            } catch (IOException e) {
                log.error("[ChatAttachment] 读盘失败 id={}", id, e);
                throw new IllegalStateException("读取附件失败：" + id);
            }

            BillParseResultDto parsed = billImageParseService.parseImage(bytes, att.getMimeType());
            blocks.add(formatParseBlock(index++, id, parsed));
            repository.markReferenced(id, now);
        }

        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(text)) {
            sb.append("用户消息：").append(text).append("\n\n");
        } else {
            sb.append(DEFAULT_IMAGE_ONLY_PROMPT).append("\n\n");
        }
        sb.append("【附件账单识别结果｜待用户确认】\n");
        for (String block : blocks) {
            sb.append(block).append('\n');
        }
        sb.append('\n').append(CONFIRM_BEFORE_WRITE_INSTRUCTION);
        return sb.toString();
    }

    private ChatAttachment requireOwned(int userId, String id) {
        ChatAttachment att = repository.findById(id);
        if (att == null || att.getUserId() != userId) {
            throw new AttachmentException(HttpStatus.NOT_FOUND, "附件不存在");
        }
        return att;
    }

    private static String formatParseBlock(int index, String attachmentId, BillParseResultDto parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(") 附件 ").append(attachmentId);
        List<BillParseItemDto> items = parsed.getItems() == null ? List.of() : parsed.getItems();
        if (items.isEmpty()) {
            sb.append("：未识别到可记账流水");
            return sb.toString();
        }
        sb.append("：\n");
        int i = 1;
        for (BillParseItemDto item : items) {
            sb.append("  - 流水").append(i++).append("：")
                    .append(handleLabel(item.getHandle()))
                    .append(" 金额=").append(nullToEmpty(item.getMoney()))
                    .append(" 日期=").append(blankOrUnknown(item.getDate()))
                    .append(" 商户=").append(blankOrUnknown(item.getMerchant()))
                    .append(" 账户提示=").append(blankOrUnknown(item.getAccountNameHint()))
                    .append(" 对方账户提示=").append(blankOrUnknown(item.getAccountToNameHint()))
                    .append(" 分类提示=").append(blankOrUnknown(item.getTypeNameHint()))
                    .append(" 备注=").append(blankOrUnknown(item.getNote()));
            if (item.getConfidence() != null) {
                sb.append(" 置信度=").append(item.getConfidence());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String handleLabel(Integer handle) {
        if (handle == null) {
            return "支出";
        }
        return switch (handle) {
            case 0 -> "收入";
            case 2 -> "转账";
            default -> "支出";
        };
    }

    private static String blankOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未知";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public ChatAttachmentResponseDto toDto(ChatAttachment att) {
        return ChatAttachmentResponseDto.builder()
                .id(att.getId())
                .kind(att.getKind())
                .mimeType(att.getMimeType())
                .sizeBytes(att.getSizeBytes())
                .width(att.getWidth())
                .height(att.getHeight())
                .url(null)
                .expiresAt(formatIso(att.getExpiresAt()))
                .createdAt(formatIso(att.getCreatedAt()))
                .build();
    }

    private static String formatIso(Date date) {
        if (date == null) {
            return null;
        }
        return ISO_OFFSET.format(date.toInstant().atZone(SHANGHAI));
    }

    private static String normalizeMime(String contentType) {
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

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/heic", "image/heif" -> ".heic";
            default -> ".jpg";
        };
    }

    private static Integer[] readImageSize(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new Integer[] {null, null};
            }
            return new Integer[] {image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            return new Integer[] {null, null};
        }
    }

    /** 供测试/运维：清理过期且未引用附件。 */
    public int gcExpiredUnreferenced() {
        List<ChatAttachment> expired = repository.findExpiredUnreferenced(new Date());
        int n = 0;
        for (ChatAttachment att : expired) {
            repository.deleteById(att.getId());
            storage.deleteQuietly(att.getStoragePath());
            n++;
        }
        return n;
    }

    public Map<String, Object> limits() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("maxBytes", properties.getMaxBytes());
        map.put("maxCount", properties.getMaxCount());
        map.put("ttlHours", properties.getTtlHours());
        return map;
    }
}
