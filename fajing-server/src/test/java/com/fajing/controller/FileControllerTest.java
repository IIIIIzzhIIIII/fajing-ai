package com.fajing.controller;

import com.fajing.service.FileAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地图片访问：公开图直接放行，生成图必须带有效签名。
 */
class FileControllerTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";

    @TempDir
    Path uploadDir;

    private FileAccessService fileAccessService;
    private FileController controller;

    @BeforeEach
    void setUp() throws Exception {
        fileAccessService = new FileAccessService();
        ReflectionTestUtils.setField(fileAccessService, "secret", SECRET);
        ReflectionTestUtils.setField(fileAccessService, "ttlSeconds", 600L);

        controller = new FileController(fileAccessService);
        ReflectionTestUtils.setField(controller, "uploadDir", uploadDir.toString());

        Files.write(uploadDir.resolve("ai_1_f1_1.png"), new byte[]{1, 2, 3});
        Files.write(uploadDir.resolve("style-f13.jpg"), new byte[]{4, 5});
    }

    @Test
    void publicFileIsServed() throws Exception {
        ResponseEntity<byte[]> resp = controller.file("style-f13.jpg", null, null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsExactly(new byte[]{4, 5});
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(resp.getHeaders().getCacheControl()).contains("public");
    }

    @Test
    void generatedFileWithoutSignatureIsForbidden() {
        assertThatThrownBy(() -> controller.file("ai_1_f1_1.png", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void generatedFileWithValidSignatureIsServed() throws Exception {
        String signed = fileAccessService.sign("/uploads/ai_1_f1_1.png");
        long exp = expOf(signed);
        String sig = sigOf(signed);

        ResponseEntity<byte[]> resp = controller.file("ai_1_f1_1.png", exp, sig);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsExactly(new byte[]{1, 2, 3});
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(resp.getHeaders().getCacheControl()).contains("private");
    }

    @Test
    void expiredSignatureIsForbidden() {
        ReflectionTestUtils.setField(fileAccessService, "ttlSeconds", -10L);
        String signed = fileAccessService.sign("/uploads/ai_1_f1_1.png");

        assertThatThrownBy(() -> controller.file("ai_1_f1_1.png", expOf(signed), sigOf(signed)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void missingFileReturnsNotFound() {
        assertThatThrownBy(() -> controller.file("style-missing.jpg", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> controller.file("../secret.png", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> controller.file("a/b.png", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    private static long expOf(String url) {
        Matcher m = Pattern.compile("[?&]e=(\\d+)").matcher(url);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    private static String sigOf(String url) {
        Matcher m = Pattern.compile("[?&]s=([A-Za-z0-9_-]+)").matcher(url);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
