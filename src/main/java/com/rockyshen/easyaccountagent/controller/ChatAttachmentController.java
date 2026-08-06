package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dto.ChatAttachmentContent;
import com.rockyshen.easyaccountagent.dto.ChatAttachmentResponseDto;
import com.rockyshen.easyaccountagent.service.AttachmentException;
import com.rockyshen.easyaccountagent.service.ChatAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/chat/attachments")
@RequiredArgsConstructor
public class ChatAttachmentController {

    private final ChatAttachmentService chatAttachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "kind", required = false) String kind) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未授权"));
        }
        try {
            ChatAttachmentResponseDto dto = chatAttachmentService.upload(userId, file, kind);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto);
        } catch (AttachmentException e) {
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[ChatAttachment] 上传失败 userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "上传失败"));
        }
    }

    /**
     * P0：按 variant 返回缩略图 / 原图字节。
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<?> content(@PathVariable("id") String id,
                                     @RequestParam(value = "variant", required = false) String variant) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "未授权"));
        }
        try {
            ChatAttachmentContent content = chatAttachmentService.getContent(userId, id, variant);
            MediaType mediaType = parseMediaType(content.mimeType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.bytes().length))
                    .body(content.bytes());
        } catch (AttachmentException e) {
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[ChatAttachment] 读取内容失败 id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "读取附件失败"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable("id") String id) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未授权"));
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatAttachmentService.getForUser(userId, id));
        } catch (AttachmentException e) {
            // 跨用户按文档返回 404，勿暴露存在性
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        Integer userId = AuthContext.getUserIdOrNull();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "未授权"));
        }
        try {
            chatAttachmentService.deleteForUser(userId, id);
            return ResponseEntity.noContent().build();
        } catch (AttachmentException e) {
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private static MediaType parseMediaType(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
