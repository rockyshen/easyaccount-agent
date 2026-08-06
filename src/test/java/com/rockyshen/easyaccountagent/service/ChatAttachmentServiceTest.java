package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.config.ChatAttachmentProperties;
import com.rockyshen.easyaccountagent.dao.ChatAttachmentJdbcRepository;
import com.rockyshen.easyaccountagent.dto.BillParseItemDto;
import com.rockyshen.easyaccountagent.dto.BillParseResultDto;
import com.rockyshen.easyaccountagent.dto.ChatAttachmentContent;
import com.rockyshen.easyaccountagent.dto.ChatAttachmentResponseDto;
import com.rockyshen.easyaccountagent.entity.ChatAttachment;
import com.rockyshen.easyaccountagent.storage.LocalAttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    private ChatAttachmentJdbcRepository repository;
    @Mock
    private BillImageParseService billImageParseService;

    @TempDir
    Path tempDir;

    private LocalAttachmentStorage storage;
    private ChatAttachmentProperties properties;
    private ChatAttachmentService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new ChatAttachmentProperties();
        properties.setMaxBytes(1024 * 1024);
        properties.setMaxCount(9);
        properties.setTtlHours(24);
        properties.setReferencedRetentionDays(90);
        properties.setPublicBaseUrl("http://example.test:6088");
        properties.setStorageDir(tempDir.toString());
        storage = new LocalAttachmentStorage(properties);
        storage.init();
        service = new ChatAttachmentService(repository, storage, properties, billImageParseService);
    }

    @Test
    void upload_persistsMetadataAndReturnsDtoWithUrls() throws Exception {
        byte[] jpeg = sampleJpeg(400, 300);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", jpeg);

        ChatAttachmentResponseDto dto = service.upload(7, file, "image");

        assertTrue(dto.getId().startsWith("att_"));
        assertEquals("image", dto.getKind());
        assertEquals("image/jpeg", dto.getMimeType());
        assertEquals(jpeg.length, dto.getSizeBytes());
        assertEquals(400, dto.getWidth());
        assertEquals(300, dto.getHeight());
        assertNotNull(dto.getThumbWidth());
        assertNotNull(dto.getThumbHeight());
        assertTrue(dto.getThumbWidth() <= 256);
        assertTrue(dto.getThumbHeight() <= 256);
        assertTrue(dto.getUrl().contains("/content?variant=original"));
        assertTrue(dto.getThumbnailUrl().contains("/content?variant=thumbnail"));
        assertTrue(dto.getUrl().startsWith("http://example.test:6088/"));
        assertNotNull(dto.getExpiresAt());
        assertNotNull(dto.getCreatedAt());

        ArgumentCaptor<ChatAttachment> captor = ArgumentCaptor.forClass(ChatAttachment.class);
        verify(repository).insert(captor.capture());
        ChatAttachment saved = captor.getValue();
        assertEquals(7, saved.getUserId());
        assertFalse(saved.isReferenced());
        assertTrue(storage.resolve(saved.getStoragePath()).toFile().exists());
        assertNotNull(saved.getThumbStoragePath());
        assertTrue(storage.resolve(saved.getThumbStoragePath()).toFile().exists());
        assertTrue(saved.getStoragePath().contains("/original"));
        assertTrue(saved.getThumbStoragePath().endsWith("/thumb.jpg"));
    }

    @Test
    void upload_rejectsUnsupportedMime() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", new byte[] {1, 2, 3});
        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.upload(1, file, "image"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
    }

    @Test
    void upload_rejectsTooLarge() {
        properties.setMaxBytes(3);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.upload(1, file, null));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
    }

    @Test
    void getContent_returnsOriginalAndThumbnail() throws Exception {
        byte[] jpeg = sampleJpeg(320, 240);
        ChatAttachment att = baseAtt("att_c", 1);
        att.setStoragePath(storage.writeOriginal(1, "att_c", ".jpg", jpeg));
        att.setThumbStoragePath(storage.writeThumb(1, "att_c", sampleJpeg(160, 120)));
        att.setThumbWidth(160);
        att.setThumbHeight(120);
        when(repository.findById("att_c")).thenReturn(att);

        ChatAttachmentContent original = service.getContent(1, "att_c", "original");
        assertEquals("image/jpeg", original.mimeType());
        assertArrayEquals(jpeg, original.bytes());

        ChatAttachmentContent thumb = service.getContent(1, "att_c", "thumbnail");
        assertEquals("image/jpeg", thumb.mimeType());
        assertTrue(thumb.bytes().length > 0);
        assertNotEquals(jpeg.length, thumb.bytes().length);
    }

    @Test
    void getContent_generatesThumbOnDemandWhenMissing() throws Exception {
        byte[] jpeg = sampleJpeg(500, 400);
        ChatAttachment att = baseAtt("att_d", 2);
        att.setStoragePath(storage.writeOriginal(2, "att_d", ".jpg", jpeg));
        att.setThumbStoragePath(null);
        when(repository.findById("att_d")).thenReturn(att);

        ChatAttachmentContent thumb = service.getContent(2, "att_d", "thumbnail");
        assertEquals("image/jpeg", thumb.mimeType());
        assertTrue(thumb.bytes().length > 0);
        verify(repository).updateThumb(eq("att_d"), contains("thumb.jpg"), anyInt(), anyInt());
    }

    @Test
    void getContent_rejectsInvalidVariantAndCrossUser() throws Exception {
        ChatAttachment att = baseAtt("att_e", 9);
        att.setStoragePath(storage.writeOriginal(9, "att_e", ".jpg", sampleJpeg(10, 10)));
        when(repository.findById("att_e")).thenReturn(att);

        AttachmentException badVariant = assertThrows(AttachmentException.class,
                () -> service.getContent(9, "att_e", "full"));
        assertEquals(HttpStatus.BAD_REQUEST, badVariant.getStatus());
        assertEquals("不支持的 variant", badVariant.getMessage());

        AttachmentException cross = assertThrows(AttachmentException.class,
                () -> service.getContent(1, "att_e", "original"));
        assertEquals(HttpStatus.NOT_FOUND, cross.getStatus());
    }

    @Test
    void getContent_allowsReferencedPastShortTtl() throws Exception {
        byte[] jpeg = sampleJpeg(20, 20);
        ChatAttachment att = baseAtt("att_f", 1);
        att.setReferenced(true);
        att.setExpiresAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        att.setStoragePath(storage.writeOriginal(1, "att_f", ".jpg", jpeg));
        when(repository.findById("att_f")).thenReturn(att);

        ChatAttachmentContent content = service.getContent(1, "att_f", "original");
        assertArrayEquals(jpeg, content.bytes());
    }

    @Test
    void getContent_rejectsExpiredUnreferenced() throws Exception {
        ChatAttachment att = baseAtt("att_g", 1);
        att.setReferenced(false);
        att.setExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        att.setStoragePath(storage.writeOriginal(1, "att_g", ".jpg", sampleJpeg(8, 8)));
        when(repository.findById("att_g")).thenReturn(att);

        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.getContent(1, "att_g", "original"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void assertUsableForChat_rejectsForeignOrExpired() {
        ChatAttachment foreign = baseAtt("att_a", 2);
        when(repository.findById("att_a")).thenReturn(foreign);
        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.assertUsableForChat(1, List.of("att_a")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("附件无效或已过期", ex.getMessage());

        ChatAttachment expired = baseAtt("att_b", 1);
        expired.setExpiresAt(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        when(repository.findById("att_b")).thenReturn(expired);
        AttachmentException ex2 = assertThrows(AttachmentException.class,
                () -> service.assertUsableForChat(1, List.of("att_b")));
        assertEquals("附件无效或已过期", ex2.getMessage());
    }

    @Test
    void assertUsableForChat_rejectsTooMany() {
        List<String> ids = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.assertUsableForChat(1, ids));
        assertEquals("附件数量超过限制", ex.getMessage());
    }

    @Test
    void buildAgentInput_parsesAndMarksReferencedWithLongRetention() throws Exception {
        ChatAttachment att = baseAtt("att_x", 3);
        String relative = storage.writeOriginal(3, "att_x", ".jpg", new byte[] {9, 9, 9});
        att.setStoragePath(relative);
        when(repository.findById("att_x")).thenReturn(att);

        BillParseItemDto item = new BillParseItemDto();
        item.setHandle(1);
        item.setMoney("23.00");
        item.setDate("2026-08-04");
        item.setMerchant("便利店");
        item.setTypeNameHint("餐饮");
        BillParseResultDto parsed = new BillParseResultDto();
        parsed.setItems(List.of(item));
        when(billImageParseService.parseImage(any(), eq("image/jpeg"))).thenReturn(parsed);

        String agentInput = service.buildAgentInput(3, "帮我记一下", List.of("att_x"));

        assertTrue(agentInput.contains("用户消息：帮我记一下"));
        assertTrue(agentInput.contains("【附件账单识别结果｜待用户确认】"));
        assertTrue(agentInput.contains("金额=23.00"));
        assertTrue(agentInput.contains("便利店"));
        assertTrue(agentInput.contains("本轮严禁调用任何写入类工具"));
        assertTrue(agentInput.contains("addExpense"));
        assertFalse(agentInput.contains("完成记账"));

        ArgumentCaptor<Date> expiresCaptor = ArgumentCaptor.forClass(Date.class);
        verify(repository).markReferenced(eq("att_x"), any(Date.class), expiresCaptor.capture());
        Instant expires = expiresCaptor.getValue().toInstant();
        assertTrue(expires.isAfter(Instant.now().plus(80, ChronoUnit.DAYS)));
        assertTrue(expires.isBefore(Instant.now().plus(100, ChronoUnit.DAYS)));
    }

    @Test
    void buildAgentInput_imageOnlyUsesDefaultPrompt() throws Exception {
        ChatAttachment att = baseAtt("att_y", 1);
        att.setStoragePath(storage.writeOriginal(1, "att_y", ".jpg", new byte[] {1}));
        when(repository.findById("att_y")).thenReturn(att);
        BillParseResultDto empty = new BillParseResultDto();
        empty.setItems(List.of());
        when(billImageParseService.parseImage(any(), any())).thenReturn(empty);

        String agentInput = service.buildAgentInput(1, "  ", List.of("att_y"));
        assertTrue(agentInput.contains("用户发送了账单图片"));
        assertTrue(agentInput.contains("未识别到可记账流水"));
        assertTrue(agentInput.contains(ChatAttachmentService.CONFIRM_BEFORE_WRITE_INSTRUCTION));
    }

    @Test
    void delete_rejectsReferenced() {
        ChatAttachment att = baseAtt("att_z", 1);
        att.setReferenced(true);
        when(repository.findById("att_z")).thenReturn(att);
        AttachmentException ex = assertThrows(AttachmentException.class,
                () -> service.deleteForUser(1, "att_z"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(repository, never()).deleteById(any());
    }

    private static ChatAttachment baseAtt(String id, int userId) {
        ChatAttachment att = new ChatAttachment();
        att.setId(id);
        att.setUserId(userId);
        att.setKind("image");
        att.setMimeType("image/jpeg");
        att.setSizeBytes(3);
        att.setStoragePath("u-" + userId + "/" + id + "/original.jpg");
        att.setReferenced(false);
        att.setCreatedAt(new Date());
        att.setExpiresAt(Date.from(Instant.now().plus(12, ChronoUnit.HOURS)));
        return att;
    }

    private static byte[] sampleJpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bos);
        return bos.toByteArray();
    }
}
