package com.campusbridge.tool;

/** 令牌校验结果。失败一定给原因，评审时能直接说清「为什么这张卡不能点了」。 */
public record TokenCheck(boolean ok, String reason, ConfirmToken token) {

    public static TokenCheck pass(ConfirmToken token) {
        return new TokenCheck(true, null, token);
    }

    public static TokenCheck fail(String reason) {
        return new TokenCheck(false, reason, null);
    }
}
