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
import java.util.Comparator;
import java.util.stream.Stream;

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

    /**
     * 写入原图：chat-attachments/u-{userId}/{attachmentId}/original{ext}
     */
    public String writeOriginal(int userId, String attachmentId, String extension, byte[] bytes) throws IOException {
        Path dir = attachmentDir(userId, attachmentId);
        Files.createDirectories(dir);
        String relative = relativeDir(userId, attachmentId) + "/original" + extension;
        Path target = resolve(relative);
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return relative;
    }

    /**
     * 写入缩略图：.../{attachmentId}/thumb.jpg
     */
    public String writeThumb(int userId, String attachmentId, byte[] jpegBytes) throws IOException {
        Path dir = attachmentDir(userId, attachmentId);
        Files.createDirectories(dir);
        String relative = relativeDir(userId, attachmentId) + "/thumb.jpg";
        Path target = resolve(relative);
        Files.write(target, jpegBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return relative;
    }

    /** 兼容旧路径：u-{userId}/{attachmentId}{ext} */
    public String write(int userId, String attachmentId, String extension, byte[] bytes) throws IOException {
        return writeOriginal(userId, attachmentId, extension, bytes);
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        return Files.isRegularFile(resolve(relativePath));
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

    /**
     * 删除附件目录（新结构）及显式路径上的文件（兼容旧扁平路径）。
     */
    public void deleteAttachmentQuietly(int userId, String attachmentId,
                                        String originalPath, String thumbPath) {
        deleteQuietly(originalPath);
        deleteQuietly(thumbPath);
        Path dir = attachmentDir(userId, attachmentId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("[ChatAttachment] 删除目录项失败 path={}: {}", p, e.toString());
                }
            });
        } catch (Exception e) {
            log.warn("[ChatAttachment] 清理附件目录失败 userId={} id={}: {}", userId, attachmentId, e.toString());
        }
    }

    private Path attachmentDir(int userId, String attachmentId) {
        return resolve(relativeDir(userId, attachmentId));
    }

    private static String relativeDir(int userId, String attachmentId) {
        return "u-" + userId + "/" + attachmentId;
    }
}
