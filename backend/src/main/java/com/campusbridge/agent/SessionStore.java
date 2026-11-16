package com.campusbridge.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 会话仓库。M1 只在内存里；重启丢失是已知限制，写成事件而不是假装持久化。 */
@Component
public class SessionStore {

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    public void save(AgentSession session) {
        sessions.put(session.id(), session);
    }

    public AgentSession get(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new AssistantException(404, "SESSION_NOT_FOUND", "会话不存在或已过期：" + sessionId);
        }
        return session;
    }

    public int size() {
        return sessions.size();
    }
}
