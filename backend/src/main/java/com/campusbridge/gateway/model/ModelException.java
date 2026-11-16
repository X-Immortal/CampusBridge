package com.campusbridge.gateway.model;

/** 模型不可用、超时、返回不可解析 —— 一律走这个异常，由网关决定降级。 */
public class ModelException extends RuntimeException {

    public ModelException(String message) {
        super(message);
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
