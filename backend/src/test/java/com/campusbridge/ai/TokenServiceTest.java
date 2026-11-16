package com.campusbridge.ai;

import com.campusbridge.config.AiProperties;
import com.campusbridge.tool.ConfirmToken;
import com.campusbridge.tool.TokenCheck;
import com.campusbridge.tool.TokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 确认令牌：绑定版本与参数摘要、只能用一次、过期就废。 */
class TokenServiceTest {

    private final AiProperties properties = AiFixture.properties("rule");

    @Test
    void 签发后可以校验通过() {
        TokenService service = new TokenService(properties, AiFixture.CLOCK);
        ConfirmToken token = service.issue("s-1", "createTask", 3, "digest-A");
        TokenCheck check = service.verify(token.value());
        assertThat(check.ok()).isTrue();
        assertThat(check.token().taskVersion()).isEqualTo(3);
        assertThat(check.token().paramDigest()).isEqualTo("digest-A");
    }

    @Test
    void 用过一次就失效() {
        TokenService service = new TokenService(properties, AiFixture.CLOCK);
        ConfirmToken token = service.issue("s-1", "createTask", 3, "digest-A");
        service.consume(token.value());
        assertThat(service.verify(token.value()).ok()).isFalse();
    }

    @Test
    void 改过的令牌被识破() {
        TokenService service = new TokenService(properties, AiFixture.CLOCK);
        ConfirmToken token = service.issue("s-1", "createTask", 3, "digest-A");
        String tampered = token.value().substring(0, token.value().length() - 2) + "zz";
        assertThat(service.verify(tampered).ok()).isFalse();
    }

    @Test
    void 过期之后失效() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-05T01:30:00Z"));
        TokenService service = new TokenService(properties, clock);
        ConfirmToken token = service.issue("s-1", "createTask", 3, "digest-A");
        clock.advanceSeconds(301);
        TokenCheck check = service.verify(token.value());
        assertThat(check.ok()).isFalse();
        assertThat(check.reason()).contains("过期");
    }

    @Test
    void 空令牌直接拒绝() {
        TokenService service = new TokenService(properties, AiFixture.CLOCK);
        assertThat(service.verify(null).ok()).isFalse();
        assertThat(service.verify("  ").ok()).isFalse();
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return AiFixture.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
