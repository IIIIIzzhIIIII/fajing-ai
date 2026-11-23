package com.fajing.service;

import com.fajing.service.storage.StorageUrlSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 私有图片签名：公开图不签名、生成图签名可验证、过期/篡改/缺参一律拒绝。
 */
class FileAccessServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";
    private static final String PRIVATE_NAME = "ai_1_f1_123.png";
    private static final String PRIVATE_URL = "http://localhost:8081/uploads/" + PRIVATE_NAME;

    private FileAccessService service;

    @BeforeEach
    void setUp() {
        service = new FileAccessService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "ttlSeconds", 600L);
    }

    @Test
    void publicImagesAreNotSigned() {
        assertThat(service.sign("/uploads/style-f13.jpg"))
                .isEqualTo("/uploads/style-f13.jpg");
        assertThat(service.sign("http://localhost:8081/uploads/ai_model_123.png"))
                .isEqualTo("http://localhost:8081/uploads/ai_model_123.png");
        assertThat(service.sign("https://cdn.example.com/a.png"))
                .isEqualTo("https://cdn.example.com/a.png");
        assertThat(service.sign(null)).isNull();
        assertThat(service.sign("")).isEmpty();
    }

    @Test
    void generatedImageIsSignedWithExpiry() {
        long before = Instant.now().getEpochSecond();
        String signed = service.sign(PRIVATE_URL);
        long after = Instant.now().getEpochSecond();

        assertThat(signed).startsWith(PRIVATE_URL + "?e=").contains("&s=");
        long exp = expOf(signed);
        assertThat(exp).isBetween(before + 600, after + 600);
        assertThat(service.verify(PRIVATE_NAME, exp, sigOf(signed))).isTrue();
    }

    @Test
    void relativePathIsSigned() {
        String signed = service.sign("/uploads/" + PRIVATE_NAME);
        assertThat(signed).startsWith("/uploads/" + PRIVATE_NAME + "?e=");
        assertThat(service.verify(PRIVATE_NAME, expOf(signed), sigOf(signed))).isTrue();
    }

    @Test
    void expiredSignatureIsRejected() {
        ReflectionTestUtils.setField(service, "ttlSeconds", -10L);
        String signed = service.sign(PRIVATE_URL);

        assertThat(service.verify(PRIVATE_NAME, expOf(signed), sigOf(signed))).isFalse();
    }

    @Test
    void tamperedSignatureOrNameIsRejected() {
        String signed = service.sign(PRIVATE_URL);
        long exp = expOf(signed);
        String sig = sigOf(signed);

        assertThat(service.verify(PRIVATE_NAME, exp, sig + "x")).isFalse();
        assertThat(service.verify("ai_1_f1_999.png", exp, sig)).isFalse();
        assertThat(service.verify(PRIVATE_NAME, exp, null)).isFalse();
        assertThat(service.verify(PRIVATE_NAME, null, sig)).isFalse();
        assertThat(service.verify(PRIVATE_NAME, exp, "")).isFalse();
    }

    @Test
    void publicFileAlwaysPassesVerification() {
        assertThat(service.verify("style-f13.jpg", null, null)).isTrue();
        assertThat(service.verify("ai_model_123.png", null, null)).isTrue();
    }

    @Test
    void s3PrivateImageIsPresigned() {
        ReflectionTestUtils.setField(service, "storageUrlSigner",
                (StorageUrlSigner) (url, ttl) -> url + "?X-Amz-Signature=test");
        String url = "https://cdn.example.com/fajing/ai_1_f1_1.png";

        assertThat(service.sign(url)).isEqualTo(url + "?X-Amz-Signature=test");
        assertThat(service.sign("https://cdn.example.com/fajing/style-f13.jpg"))
                .isEqualTo("https://cdn.example.com/fajing/style-f13.jpg");
        assertThat(service.sign("https://cdn.example.com/fajing/ai_model_1.png"))
                .isEqualTo("https://cdn.example.com/fajing/ai_model_1.png");
    }

    @Test
    void s3UrlKeepsOriginalWithoutSigner() {
        String url = "https://cdn.example.com/fajing/ai_1_f1_1.png";
        assertThat(service.sign(url)).isEqualTo(url);
    }

    @Test
    void blankSecretFailsLoudly() {
        ReflectionTestUtils.setField(service, "secret", "");
        assertThatThrownBy(() -> service.sign(PRIVATE_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token.secret");
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
