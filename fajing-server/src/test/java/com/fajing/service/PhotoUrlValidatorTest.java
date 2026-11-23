package com.fajing.service;

import com.fajing.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 照片地址白名单：本站 /uploads、S3 公网域名放行，临时路径与任意外链拒绝。
 */
class PhotoUrlValidatorTest {

    private PhotoUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhotoUrlValidator();
        ReflectionTestUtils.setField(validator, "baseUrl", "https://api.example.com");
        ReflectionTestUtils.setField(validator, "s3PublicBaseUrl", "https://cdn.example.com");
    }

    @Test
    void localUploadPathsAreAllowed() {
        assertThat(validator.check("/uploads/a.jpg")).isEqualTo("/uploads/a.jpg");
        assertThat(validator.check("https://api.example.com/uploads/a.jpg"))
                .isEqualTo("https://api.example.com/uploads/a.jpg");
        assertThat(validator.check("https://cdn.example.com/fajing/ai_1.png"))
                .isEqualTo("https://cdn.example.com/fajing/ai_1.png");
    }

    @Test
    void temporaryPathsAreRejected() {
        assertThatThrownBy(() -> validator.check("http://tmp/a.jpg"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未上传成功");
        assertThatThrownBy(() -> validator.check("wxfile://a.jpg"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void externalOrWrongPathIsRejected() {
        assertThatThrownBy(() -> validator.check("http://evil.com/uploads/a.jpg"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不合法");
        assertThatThrownBy(() -> validator.check("https://api.example.com/other/a.jpg"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不合法");
        assertThatThrownBy(() -> validator.check("https://api.example.com:8443/uploads/a.jpg"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void blankIsRejected() {
        assertThatThrownBy(() -> validator.check(""))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先上传");
    }
}
