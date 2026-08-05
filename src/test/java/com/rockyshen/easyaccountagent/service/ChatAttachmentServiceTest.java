package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.config.ChatAttachmentProperties;
import com.rockyshen.easyaccountagent.dao.ChatAttachmentJdbcRepository;
import com.rockyshen.easyaccountagent.dto.BillParseItemDto;
import com.rockyshen.easyaccountagent.dto.BillParseResultDto;
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
        properties.setStorageDir(tempDir.toString());
        storage = new LocalAttachmentStorage(properties);
        storage.init();
        service = new ChatAttachmentService(repository, storage, properties, billImageParseService);
    }

    @Test
    void upload_persistsMetadataAndReturnsDto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3});

        ChatAttachmentResponseDto dto = service.upload(7, file, "image");

        assertTrue(dto.getId().startsWith("att_"));
        assertEquals("image", dto.getKind());
        assertEquals("image/jpeg", dto.getMimeType());
        assertEquals(5, dto.getSizeBytes());
        assertNotNull(dto.getExpiresAt());
        assertNotNull(dto.getCreatedAt());

        ArgumentCaptor<ChatAttachment> captor = ArgumentCaptor.forClass(ChatAttachment.class);
        verify(repository).insert(captor.capture());
        assertEquals(7, captor.getValue().getUserId());
        assertFalse(captor.getValue().isReferenced());
        assertTrue(storage.resolve(captor.getValue().getStoragePath()).toFile().exists());
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
    void buildAgentInput_parsesAndMarksReferenced() throws Exception {
        ChatAttachment att = baseAtt("att_x", 3);
        String relative = storage.write(3, "att_x", ".jpg", new byte[] {9, 9, 9});
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
        assertTrue(agentInput.contains("【附件账单识别结果】"));
        assertTrue(agentInput.contains("金额=23.00"));
        assertTrue(agentInput.contains("便利店"));
        verify(repository).markReferenced(eq("att_x"), any(Date.class));
    }

    @Test
    void buildAgentInput_imageOnlyUsesDefaultPrompt() throws Exception {
        ChatAttachment att = baseAtt("att_y", 1);
        att.setStoragePath(storage.write(1, "att_y", ".jpg", new byte[] {1}));
        when(repository.findById("att_y")).thenReturn(att);
        BillParseResultDto empty = new BillParseResultDto();
        empty.setItems(List.of());
        when(billImageParseService.parseImage(any(), any())).thenReturn(empty);

        String agentInput = service.buildAgentInput(1, "  ", List.of("att_y"));
        assertTrue(agentInput.contains("用户发送了图片"));
        assertTrue(agentInput.contains("未识别到可记账流水"));
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
        att.setStoragePath("u-" + userId + "/" + id + ".jpg");
        att.setReferenced(false);
        att.setCreatedAt(new Date());
        att.setExpiresAt(Date.from(Instant.now().plus(12, ChronoUnit.HOURS)));
        return att;
    }
}
