package com.fajing.service;

import com.fajing.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地限流：生成、游客建档、登录尝试三条规则。
 */
class RateLimitServiceTest {

    private final RateLimitService service = new RateLimitService();

    @Test
    void generateIsThrottledPerUser() {
        service.checkGenerate(1L);
        assertThatThrownBy(() -> service.checkGenerate(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
        assertThatCode(() -> service.checkGenerate(2L)).doesNotThrowAnyException();
    }

    @Test
    void guestCreationIsLimitedPerIp() {
        for (int i = 0; i < 20; i++) {
            service.checkGuestCreate("203.0.113.10");
        }
        assertThatThrownBy(() -> service.checkGuestCreate("203.0.113.10"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
        assertThatCode(() -> service.checkGuestCreate("203.0.113.11")).doesNotThrowAnyException();
    }

    @Test
    void loginAttemptsAreLimitedPerScopeAndIp() {
        for (int i = 0; i < 20; i++) {
            service.checkLogin("admin", "203.0.113.20");
        }
        assertThatThrownBy(() -> service.checkLogin("admin", "203.0.113.20"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
        // 不同 scope / IP 互不影响
        assertThatCode(() -> service.checkLogin("guest", "203.0.113.20")).doesNotThrowAnyException();
        assertThatCode(() -> service.checkLogin("admin", "203.0.113.21")).doesNotThrowAnyException();
    }
}
