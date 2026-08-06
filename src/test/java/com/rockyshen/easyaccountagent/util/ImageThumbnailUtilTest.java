package com.rockyshen.easyaccountagent.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImageThumbnailUtilTest {

    @Test
    void createJpegThumbnail_scalesLongestEdge() throws Exception {
        byte[] src = solidJpeg(800, 400, Color.RED);
        var result = ImageThumbnailUtil.createJpegThumbnail(src, 256, 0.75f);
        assertTrue(result.isPresent());
        assertEquals(256, result.get().width());
        assertEquals(128, result.get().height());
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(result.get().jpegBytes()));
        assertNotNull(thumb);
        assertEquals(256, thumb.getWidth());
        assertEquals(128, thumb.getHeight());
    }

    @Test
    void createJpegThumbnail_doesNotUpscale() throws Exception {
        byte[] src = solidJpeg(100, 80, Color.GREEN);
        var result = ImageThumbnailUtil.createJpegThumbnail(src, 256, 0.8f);
        assertTrue(result.isPresent());
        assertEquals(100, result.get().width());
        assertEquals(80, result.get().height());
    }

    @Test
    void createJpegThumbnail_returnsEmptyForGarbage() {
        assertTrue(ImageThumbnailUtil.createJpegThumbnail(new byte[] {1, 2, 3}, 256, 0.7f).isEmpty());
    }

    private static byte[] solidJpeg(int w, int h, Color color) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bos);
        return bos.toByteArray();
    }
}
