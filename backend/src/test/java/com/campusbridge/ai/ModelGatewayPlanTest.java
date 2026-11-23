package com.campusbridge.ai;

import com.campusbridge.config.AiProperties;
import com.campusbridge.events.AiEvent;
import com.campusbridge.events.EventRecorder;
import com.campusbridge.events.VersionInfo;
import com.campusbridge.gateway.model.FaultSwitch;
import com.campusbridge.gateway.model.ModelCache;
import com.campusbridge.gateway.model.ModelGateway;
import com.campusbridge.gateway.model.ModelQuota;
import com.campusbridge.gateway.model.ModelResult;
import com.campusbridge.gateway.model.ModelRouter;
import com.campusbridge.gateway.model.ModelUnderstanding;
import com.campusbridge.gateway.model.OpenAiCompatibleClient;
import com.campusbridge.gateway.model.RuleModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M3 网关四项能力：路由、配额、缓存、成本。全部用假模型客户端，不依赖公网。
 */
class ModelGatewayPlanTest {

    private static final ModelUnderstanding UNDERSTANDING = new ModelUnderstanding(
            "ERRAND_PICKUP",
            Map.of("location", "北门菜鸟驿站"),
            Set.of("location"),
            List.of("完成标准用平台默认值"),
            0.82);

    @Test
    void 按任务类型路由并记录model_route() {
        AiProperties properties = AiFixture.properties("llm");
        properties.getModel().getRouting().getRules().put("PICKUP", "route-fast");
        TestKit kit = new TestKit(properties);

        kit.gateway.understand("帮我取三个快递", Map.of("requesterId", 1), "tr-route", "s-route");

        AiEvent route = kit.event("model.route");
        assertThat(route.payload()).containsEntry("routeId", "PICKUP");
        assertThat(route.payload()).containsEntry("model", "route-fast");
        assertThat(route.payload()).containsEntry("reason", "intent:PICKUP");
        verify(kit.llm).extract(eq("帮我取三个快递"), anyMap(), eq("route-fast"));
    }

    @Test
    void 缓存命中时只调用一次模型并记录cache_hit() {
        AiProperties properties = AiFixture.properties("llm");
        properties.getModel().setCacheTtlSeconds(60);
        TestKit kit = new TestKit(properties);

        ModelResult first = kit.gateway.understand("帮我取快递", Map.of("requesterId", 1), "tr-1", "s-1");
        ModelResult second = kit.gateway.understand("帮我取快递", Map.of("requesterId", 1), "tr-2", "s-1");

        assertThat(first.mode()).isEqualTo("llm");
        assertThat(second.mode()).isEqualTo("llm");
        assertThat(second.notes()).anyMatch(note -> note.contains("缓存"));
        verify(kit.llm, times(1)).extract(anyString(), anyMap(), anyString());
        assertThat(kit.event("model.cache.hit")).isNotNull();
    }

    @Test
    void 配额用尽后退回规则基线并记录quota_exceeded() {
        AiProperties properties = AiFixture.properties("llm");
        properties.getModel().setDailyQuota(1);
        TestKit kit = new TestKit(properties);

        ModelResult first = kit.gateway.understand("帮我取快递", Map.of("requesterId", 7), "tr-1", "s-1");
        ModelResult second = kit.gateway.understand("帮我去南门拿包裹", Map.of("requesterId", 7), "tr-2", "s-2");

        assertThat(first.degraded()).isFalse();
        assertThat(second.degraded()).isTrue();
        assertThat(second.mode()).isEqualTo("rule");
        assertThat(second.notes()).anyMatch(note -> note.contains("配额"));
        assertThat(kit.event("model.quota.exceeded").payload())
                .containsEntry("limit", 1)
                .containsEntry("requester", "7");
        verify(kit.llm, times(1)).extract(anyString(), anyMap(), anyString());
    }

    @Test
    void 成功调用后记录估算成本() {
        TestKit kit = new TestKit(AiFixture.properties("llm"));

        kit.gateway.understand("帮我取快递", Map.of("requesterId", 1), "tr-cost", "s-cost");

        AiEvent cost = kit.event("model.cost");
        assertThat(cost.payload()).containsKeys("inputTokens", "outputTokens", "totalTokens",
                "estimatedCost", "currency", "model", "routeId");
        assertThat((Integer) cost.payload().get("totalTokens")).isPositive();
        assertThat((Double) cost.payload().get("estimatedCost")).isPositive();
    }

    @Test
    void rule模式完全不碰模型与路由() {
        AiProperties properties = AiFixture.properties("rule");
        TestKit kit = new TestKit(properties);

        ModelResult result = kit.gateway.understand("帮我取快递", Map.of("requesterId", 1), "tr-rule", "s-rule");

        assertThat(result.mode()).isEqualTo("rule");
        assertThat(result.degraded()).isFalse();
        verifyNoInteractions(kit.llm);
        assertThat(kit.event("model.route")).isNull();
    }

    private static final class TestKit {
        private final OpenAiCompatibleClient llm = mock(OpenAiCompatibleClient.class);
        private final EventRecorder events;
        private final ModelGateway gateway;

        private TestKit(AiProperties properties) {
            when(llm.configured()).thenReturn(true);
            when(llm.extract(anyString(), anyMap(), anyString())).thenReturn(UNDERSTANDING);
            events = new EventRecorder(
                    mock(JdbcTemplate.class),
                    new ObjectMapper(),
                    new VersionInfo(properties, AiFixture.ontology()));
            gateway = new ModelGateway(
                    properties,
                    new RuleModelClient(AiFixture.CLOCK),
                    llm,
                    events,
                    new ModelRouter(properties),
                    new ModelQuota(properties, AiFixture.CLOCK),
                    new ModelCache(properties, AiFixture.CLOCK),
                    new FaultSwitch(properties));
        }

        private AiEvent event(String type) {
            return events.recent(200).stream()
                    .filter(event -> type.equals(event.type()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
