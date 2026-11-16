package com.campusbridge.schema;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 确认令牌的对外视图。值本身是 HMAC，客户端只能原样回传，不能伪造。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenView(String value, String expiresAt, String nonce, String paramDigest) {
}
