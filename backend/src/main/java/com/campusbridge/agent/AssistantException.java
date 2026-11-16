package com.campusbridge.agent;

/** 带 HTTP 状态的业务异常：缺会话、状态不对、参数非法。 */
public class AssistantException extends RuntimeException {

    private final int status;
    private final String code;

    public AssistantException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
