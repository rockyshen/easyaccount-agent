package com.rockyshen.easyaccountagent.storage;

import com.rockyshen.easyaccountagent.config.ChatAttachmentProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalAttachmentStorage {

    private final ChatAttachmentProperties properties;
    private Path root;

    @PostConstruct
    public void init() throws IOException {
        root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("[ChatAttachment] storage dir={}", root);
    }

    public Path root() {
        return root;
    }

    public Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法存储路径");
        }
        return resolved;
    }

    public String write(int userId, String attachmentId, String extension, byte[] bytes) throws IOException {
        Path userDir = root.resolve("u-" + userId);
        Files.createDirectories(userDir);
        String relative = "u-" + userId + "/" + attachmentId + extension;
        Path target = resolve(relative);
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return relative;
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    public void deleteQuietly(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception e) {
            log.warn("[ChatAttachment] 删除文件失败 path={}: {}", relativePath, e.toString());
        }
    }
}
