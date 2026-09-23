package com.taskhub;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthDoesNotDependOnDatabase() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void readinessQueriesSchemaVersionThroughMyBatis() throws Exception {
        mockMvc.perform(get("/api/ready"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.data.status").value("READY"))
            .andExpect(jsonPath("$.data.schemaVersion").value("1"));
    }

    @Test
    void arbitraryGetAndPostRequireAuthenticationWithStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101))
            .andExpect(jsonPath("$.message").value("会话已失效，请重新登录"))
            .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(post("/api/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101))
            .andExpect(jsonPath("$.message").value("会话已失效，请重新登录"))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
