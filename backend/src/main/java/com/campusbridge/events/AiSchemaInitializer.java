package com.campusbridge.events;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时建 M1 需要的表。DDL 同时兼容 MySQL 与 H2(MySQL 模式)：
 * 只用于 2.0 新增的 AI 相关表，1.0 的业务表仍由 {@code backend/sql/schema.sql} 负责。
 */
@Component
public class AiSchemaInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(AiSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AiSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_event (
                      seq BIGINT NOT NULL PRIMARY KEY,
                      event_type VARCHAR(64) NOT NULL,
                      trace_id VARCHAR(64),
                      session_id VARCHAR(64),
                      payload TEXT,
                      created_at TIMESTAMP NOT NULL
                    )
                    """);
        } catch (RuntimeException ex) {
            LOG.warn("AI 事件表初始化失败（不影响演示，事件只在内存里）：{}", ex.getMessage());
        }
    }
}
