package com.fajing.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.CRC32;

/**
 * AI 生成内容的隐式标识：把 AIGC 信息写入图片文件元数据。
 * - PNG：插入 tEXt 块（keyword=AIGC）
 * - JPEG：插入 COM 注释段
 * 标识只使用 ASCII，避免不同解码器对编码的兼容问题。
 */
public final class AigcMetadata {

    private static final String KEYWORD = "AIGC";

    private AigcMetadata() {
    }

    public static byte[] mark(byte[] image) {
        if (image == null || image.length < 12) {
            return image;
        }
        if (isPng(image)) {
            return markPng(image);
        }
        if (isJpeg(image)) {
            return markJpeg(image);
        }
        return image;
    }

    /** 标识文本：字段名参考《人工智能生成合成内容标识办法》的隐式标识要素 */
    public static String markerText() {
        return "AIGC:GeneratedBy=fajing-ai;ContentProducer=fajing-ai;ProduceID="
                + Instant.now().toEpochMilli();
    }

    private static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static byte[] markPng(byte[] src) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(src.length + 96);
            out.write(src, 0, 8);
            int pos = 8;
            boolean inserted = false;
            while (pos + 8 <= src.length) {
                int len = readInt(src, pos);
                if (len < 0 || pos + 12 + len > src.length) {
                    break;
                }
                String type = new String(src, pos + 4, 4, StandardCharsets.US_ASCII);
                if (!inserted && "IDAT".equals(type)) {
                    out.write(textChunk(KEYWORD, markerText()));
                    inserted = true;
                }
                out.write(src, pos, 12 + len);
                pos += 12 + len;
                if ("IEND".equals(type)) {
                    break;
                }
            }
            if (!inserted) {
                return src;
            }
            if (pos < src.length) {
                out.write(src, pos, src.length - pos);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return src;
        }
    }

    private static byte[] markJpeg(byte[] src) {
        try {
            byte[] text = markerText().getBytes(StandardCharsets.US_ASCII);
            int len = text.length + 2;
            if (len > 65533) {
                return src;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(src.length + len + 2);
            out.write(0xFF);
            out.write(0xD8);
            out.write(0xFF);
            out.write(0xFE);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
            out.write(text);
            out.write(src, 2, src.length - 2);
            return out.toByteArray();
        } catch (Exception e) {
            return src;
        }
    }

    private static byte[] textChunk(String keyword, String text) throws Exception {
        byte[] data = (keyword + "\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream(data.length + 12);
        writeInt(chunk, data.length);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        chunk.write(type);
        chunk.write(data);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        writeInt(chunk, (int) crc.getValue());
        return chunk.toByteArray();
    }

    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8) | (b[offset + 3] & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
