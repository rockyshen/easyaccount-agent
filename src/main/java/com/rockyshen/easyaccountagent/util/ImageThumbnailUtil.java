package com.rockyshen.easyaccountagent.util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * 生成最长边受限的 JPEG 缩略图（与 iOS 对齐：默认最长边 256、质量约 0.75）。
 */
public final class ImageThumbnailUtil {

    private ImageThumbnailUtil() {
    }

    public record ThumbResult(byte[] jpegBytes, int width, int height) {
    }

    /**
     * @return empty 表示无法解码（如 HEIC / 损坏文件）
     */
    public static Optional<ThumbResult> createJpegThumbnail(byte[] originalBytes, int maxEdge, float jpegQuality) {
        if (originalBytes == null || originalBytes.length == 0 || maxEdge < 1) {
            return Optional.empty();
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (src == null) {
                return Optional.empty();
            }
            int srcW = src.getWidth();
            int srcH = src.getHeight();
            if (srcW <= 0 || srcH <= 0) {
                return Optional.empty();
            }
            double scale = Math.min(1.0, (double) maxEdge / (double) Math.max(srcW, srcH));
            int w = Math.max(1, (int) Math.round(srcW * scale));
            int h = Math.max(1, (int) Math.round(srcH * scale));

            BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dest.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(src, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }

            float quality = Math.min(1f, Math.max(0.1f, jpegQuality));
            byte[] jpeg = writeJpeg(dest, quality);
            return Optional.of(new ThumbResult(jpeg, w, h));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", bos);
            return bos.toByteArray();
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
