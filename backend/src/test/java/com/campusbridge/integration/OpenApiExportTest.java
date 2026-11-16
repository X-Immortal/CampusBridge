package com.campusbridge.integration;

import com.campusbridge.common.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exports the actual Spring MVC mappings without connecting to external services. */
@SpringBootTest(properties = {
        "springdoc.paths-to-match=/api/v1/**",
        "springdoc.override-with-generic-response=false",
        "springdoc.writer-with-order-by-keys=true"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "exportOpenApi", matches = "true")
class OpenApiExportTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void exportEveryApplicationRoute() throws Exception {
        byte[] yaml = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        OpenAPI api = Yaml.mapper().readValue(yaml, OpenAPI.class);
        var expected = new TreeSet<String>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            mapping.getPatternValues().stream().filter(path -> path.startsWith("/api/v1/"))
                    .forEach(path -> mapping.getMethodsCondition().getMethods()
                            .forEach(verb -> expected.add(verb.name() + " " + path)));
        });
        var actual = new TreeSet<String>();
        api.getPaths().forEach((path, item) -> item.readOperationsMap()
                .forEach((verb, operation) -> actual.add(verb.name() + " " + path)));
        assertEquals(expected, actual, "OpenAPI must contain every application route and no invented route");
        Path output = Path.of("../docs/openapi.yaml");
        Files.createDirectories(output.getParent());
        Files.writeString(output, Yaml.pretty(api));
    }

    @TestConfiguration
    static class ExportConfiguration {
        @Bean
        OpenAPI apiDefinition() {
            return new OpenAPI()
                    .info(new Info().title("CampusBridge HTTP API").version("1.0")
                            .description("由当前 Controller 与 DTO 导出。Result.data 及 Map 请求体仍是动态结构；"
                                    + "业务必填与跨字段规则见 docs/srs.md，具体动态载荷见实现。"))
                    .servers(List.of(new Server().url("/")))
                    .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
        }

        @Bean
        OpenApiCustomizer securityAndErrors() {
            return api -> {
                ModelConverters.getInstance().read(ErrorResponse.class)
                        .forEach(api.getComponents()::addSchemas);
                api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((verb, operation) -> {
                    boolean publicRead = verb.name().equals("GET") && (
                            path.equals("/api/v1/tasks") || path.equals("/api/v1/tasks/{taskId}")
                            || path.equals("/api/v1/posts") || path.equals("/api/v1/posts/{postId}")
                            || path.equals("/api/v1/users/{userId}/profile")
                            || path.equals("/api/v1/users/{userId}/reviews"));
                    boolean publicRoute = publicRead || path.startsWith("/api/v1/auth/")
                            || path.startsWith("/api/v1/home/") || path.equals("/api/v1/content");
                    if (!publicRoute) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                        operation.getResponses().addApiResponse("401", error("未认证或访问令牌无效"));
                    }
                    if (path.startsWith("/api/v1/admin/")) {
                        operation.setDescription("需要当前账号具有 ADMIN 角色。");
                        operation.getResponses().addApiResponse("403", error("账号无管理权限"));
                    }
                    if (operation.getResponses().containsKey("204")) {
                        operation.getResponses().get("204").setContent(null);
                    }
                }));
            };
        }

        private ApiResponse error(String description) {
            return new ApiResponse().description(description).content(new Content()
                    .addMediaType("application/json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
        }
    }
}
