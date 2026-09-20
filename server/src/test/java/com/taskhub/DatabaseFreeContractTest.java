package com.taskhub;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskhub.infrastructure.metadata.ApplicationMetadataMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unavailable",
    "spring.datasource.hikari.initialization-fail-timeout=-1",
    "spring.datasource.hikari.connection-timeout=250"
})
@AutoConfigureMockMvc
class DatabaseFreeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationMetadataMapper metadataMapper;

    @Test
    void applicationBootsAndHealthWorksWithoutDatabase() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void readinessFailureDoesNotLeakDatabaseDetails() throws Exception {
        when(metadataMapper.findValue("schema_version"))
            .thenThrow(new MyBatisSystemException(new RuntimeException("secret jdbc details")));

        mockMvc.perform(get("/api/ready"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(503))
            .andExpect(jsonPath("$.message").value("service unavailable"))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
