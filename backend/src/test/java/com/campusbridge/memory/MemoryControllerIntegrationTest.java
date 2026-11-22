package com.campusbridge.memory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusbridge.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "memory.http.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Transactional
class MemoryControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwt;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void requiresAuthenticatedPrincipal() throws Exception {
        mvc.perform(get("/api/memory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restrictsMemoryToAuthenticatedOwner() throws Exception {
        jdbc.update("INSERT INTO users (id, username, password_hash) VALUES (2, 'memory_test_user', 'unused')");
        String owner = "Bearer " + jwt.generateToken(1L, "USER");
        String other = "Bearer " + jwt.generateToken(2L, "USER");

        String response = mvc.perform(post("/api/memory")
                        .header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"INFERRED","value":"可能偏好站内联系","userId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inferred").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        mvc.perform(get("/api/memory").param("userId", "1")
                        .header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/memory").param("userId", "1")
                        .header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/memory").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/memory").header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DECLARED","value":"测试","userId":1}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/memory/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/memory/{id}", id).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/memory").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsTemporaryMemoryWithoutExpiry() throws Exception {
        mvc.perform(post("/api/memory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(1L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEMPORARY","value":"临时地点"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
