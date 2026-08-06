package com.rockyshen.easyaccountagent.config;

import com.rockyshen.easyaccountagent.service.ChatAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清理「未引用且已过期」的短 TTL 附件；已引用附件由长期保留策略保护。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAttachmentGcScheduler {

    private final ChatAttachmentService chatAttachmentService;

    @Scheduled(fixedDelayString = "${easyaccount.attachments.gc-interval-ms:3600000}")
    public void gc() {
        try {
            int n = chatAttachmentService.gcExpiredUnreferenced();
            if (n > 0) {
                log.info("[ChatAttachment] GC removed {} expired unreferenced attachment(s)", n);
            }
        } catch (Exception e) {
            log.warn("[ChatAttachment] GC failed: {}", e.toString());
        }
    }
}
