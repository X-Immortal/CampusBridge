package com.campusbridge.agent.dto;

/**
 * 确认请求：令牌 + 幂等键。
 * 幂等键由客户端生成（同一张卡重复点击必须用同一个键），服务端据此只产生一次业务事实。
 */
public record ConfirmRequest(String token, String idempotencyKey) {
}
