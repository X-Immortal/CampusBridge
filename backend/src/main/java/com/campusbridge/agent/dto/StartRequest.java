package com.campusbridge.agent.dto;

import java.util.List;
import java.util.Map;

/** POST /api/assistant 的请求体。context 里的 requesterId / persona / forceRule 都是可选的。 */
public record StartRequest(String goal, Map<String, Object> context) {
}
