package com.campusbridge.integration;

import com.campusbridge.common.ErrorResponse;
import com.campusbridge.dto.view.PostView;
import com.campusbridge.dto.view.TaskPageView;
import com.campusbridge.entity.Task;
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
        String taskResponseRef = api.getPaths().get("/api/v1/tasks").getGet()
                .getResponses().get("200").getContent().get("*/*").getSchema().get$ref();
        Schema<?> taskResponse = api.getComponents().getSchemas()
                .get(taskResponseRef.substring(taskResponseRef.lastIndexOf('/') + 1));
        assertEquals("#/components/schemas/TaskPageView",
                taskResponse.getProperties().get("data").get$ref(),
                "Task pagination must retain its concrete data schema");

        String postResponseRef = api.getPaths().get("/api/v1/posts").getGet()
                .getResponses().get("200").getContent().get("*/*").getSchema().get$ref();
        Schema<?> postResponse = api.getComponents().getSchemas()
                .get(postResponseRef.substring(postResponseRef.lastIndexOf('/') + 1));
        String postPageRef = postResponse.getProperties().get("data").get$ref();
        Schema<?> postPage = api.getComponents().getSchemas()
                .get(postPageRef.substring(postPageRef.lastIndexOf('/') + 1));
        assertEquals("#/components/schemas/PostView", postPage.getProperties().get("items").getItems().get$ref(),
                "Nested generic pagination must retain its item schema");
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
                            .description("由当前 Controller 与 DTO 导出。请求与响应使用具体 DTO；"
                                    + "业务必填与跨字段规则见 docs/srs.md，字段结构见各接口 Schema。"))
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
                    for (int status : businessErrorStatuses(path, verb.name())) {
                        operation.getResponses().putIfAbsent(Integer.toString(status), error("业务请求失败"));
                    }
                    if (operation.getResponses().containsKey("204")) {
                        operation.getResponses().get("204").setContent(null);
                    }
                }));
            };
        }

        private List<Integer> businessErrorStatuses(String path, String method) {
            return switch (path) {
                case "/api/v1/auth/login" -> List.of(400, 401, 500);
                case "/api/v1/auth/register" -> List.of(400, 409, 500);
                case "/api/v1/auth/register/send-code" -> List.of(400, 500);
                case "/api/v1/attachments" -> List.of(400, 500, 502);
                case "/api/v1/verifications" -> method.equals("GET")
                        ? List.of(404) : List.of(400, 409, 500, 502);
                case "/api/v1/verifications/email/code" -> List.of(400, 404, 409, 500, 502);
                case "/api/v1/verifications/email/confirm", "/api/v1/verifications/current",
                        "/api/v1/admin/verifications/{id}/approve",
                        "/api/v1/admin/verifications/{id}/reject" -> List.of(400, 404, 409, 500);
                default -> List.of();
            };
        }

        private ApiResponse error(String description) {
            return new ApiResponse().description(description).content(new Content()
                    .addMediaType("application/json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
        }
    }
}
