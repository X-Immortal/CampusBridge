package com.campusbridge.tool;

import java.time.Instant;

/**
 * 确认令牌：一次授权只对应一个动作、一个版本、一份参数摘要。
 * 任务被改过（version 变了）或卡片内容变了（digest 变了），这张令牌立刻失效。
 */
public record ConfirmToken(
        String value,
        String sessionId,
        String action,
        int taskVersion,
        String paramDigest,
        Instant expiresAt,
        String nonce) {
}
