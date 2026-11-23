package com.campusbridge.gateway.model;

/**
 * 一次模型调用的路由结果。
 *
 * @param routeId 路由键（PICKUP / MOVING / PURCHASE / SUBSTITUTE / GENERAL / persona / default）
 * @param model   实际请求的模型名
 * @param reason  为什么选它，写进 {@code model.route} 事件，方便事后解释
 */
public record ModelRoute(String routeId, String model, String reason) {
}
