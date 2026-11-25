package com.fajing.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIGC 隐式标识：写入元数据后图片仍可正常解码，且包含标识文本。
 */
class AigcMetadataTest {

    @Test
    void pngKeepsDecodableAndContainsMarker() throws Exception {
        byte[] png = image("png");
        byte[] marked = AigcMetadata.mark(png);

        assertThat(new String(marked, StandardCharsets.ISO_8859_1)).contains("AIGC");
        assertThat(ImageIO.read(new ByteArrayInputStream(marked))).isNotNull();
    }

    @Test
    void jpegKeepsDecodableAndContainsMarker() throws Exception {
        byte[] jpg = image("jpg");
        byte[] marked = AigcMetadata.mark(jpg);

        assertThat(new String(marked, StandardCharsets.ISO_8859_1)).contains("AIGC");
        assertThat(ImageIO.read(new ByteArrayInputStream(marked))).isNotNull();
    }

    @Test
    void unknownFormatIsReturnedAsIs() {
        byte[] raw = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        assertThat(AigcMetadata.mark(raw)).isSameAs(raw);
        assertThat(AigcMetadata.mark(null)).isNull();
    }

    private static byte[] image(String format) throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
