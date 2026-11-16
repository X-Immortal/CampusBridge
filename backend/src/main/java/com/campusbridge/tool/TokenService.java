package com.campusbridge.tool;

import com.campusbridge.config.AiProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌签发与校验。
 *
 * <p>令牌形如 {@code v1.<nonce>.<expiresEpoch>.<hmac>}，签名覆盖
 * {@code sessionId|action|taskVersion|paramDigest|nonce|expires}。</p>
 *
 * <p>三个失效条件必须都成立才签发：动作是白名单里的、绑定了当前版本、绑定了参数摘要。
 * 校验时任何一项对不上就拒绝 —— 这就是「历史卡片必须失效」的实现。</p>
 */
@Component
public class TokenService {

    private final Clock clock;
    private final byte[] secret;
    private final int ttlSeconds;
    private final Map<String, ConfirmToken> issued = new ConcurrentHashMap<>();
    private final Set<String> used = ConcurrentHashMap.newKeySet();

    public TokenService(AiProperties properties, Clock clock) {
        this.clock = clock;
        this.ttlSeconds = properties.getToken().getTtlSeconds();
        String configured = properties.getToken().getSecret();
        String material = configured == null || configured.isBlank()
                ? UUID.randomUUID().toString()
                : configured;
        this.secret = material.getBytes(StandardCharsets.UTF_8);
    }

    public ConfirmToken issue(String sessionId, String action, int taskVersion, String paramDigest) {
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant expiresAt = Instant.now(clock).plusSeconds(ttlSeconds);
        String signingInput = signingInput(sessionId, action, taskVersion, paramDigest, nonce, expiresAt);
        String value = "v1." + nonce + "." + expiresAt.getEpochSecond() + "." + sign(signingInput);
        ConfirmToken token = new ConfirmToken(value, sessionId, action, taskVersion,
                paramDigest, expiresAt, nonce);
        issued.put(value, token);
        return token;
    }

    public TokenCheck verify(String value) {
        if (value == null || value.isBlank()) {
            return TokenCheck.fail("缺少确认令牌");
        }
        ConfirmToken token = issued.get(value);
        if (token == null) {
            return TokenCheck.fail("令牌不存在（可能已使用、已过期，或服务器重启过）");
        }
        if (used.contains(value)) {
            return TokenCheck.fail("这张确认卡已经用过了");
        }
        if (Instant.now(clock).isAfter(token.expiresAt())) {
            return TokenCheck.fail("确认卡已过期（有效期 " + ttlSeconds + " 秒）");
        }
        String expected = sign(signingInput(token.sessionId(), token.action(), token.taskVersion(),
                token.paramDigest(), token.nonce(), token.expiresAt()));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                value.substring(value.lastIndexOf('.') + 1).getBytes(StandardCharsets.UTF_8))) {
            return TokenCheck.fail("令牌签名不对，可能被改过");
        }
        return TokenCheck.pass(token);
    }

    /** 只有真正产生业务事实之后才消费令牌；校验失败不能让令牌作废。 */
    public void consume(String value) {
        ConfirmToken removed = issued.remove(value);
        if (removed != null) {
            used.add(value);
        }
    }

    public int issuedCount() {
        return issued.size();
    }

    private String signingInput(String sessionId, String action, int taskVersion,
            String paramDigest, String nonce, Instant expiresAt) {
        return sessionId + "|" + action + "|" + taskVersion + "|" + paramDigest + "|" + nonce
                + "|" + expiresAt.getEpochSecond();
    }

    private String sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法签发令牌", e);
        }
    }
}
