package com.taskhub.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IdentityBoundaryTest {
  @Autowired MockMvc mvc;

  @Test
  void missingBearerIsUnauthorized() throws Exception {
    mvc.perform(get("/api/identity/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(40101));
  }

  @Test
  void logoutWithoutSessionIsSafe() throws Exception {
    mvc.perform(post("/api/identity/logout")).andExpect(status().isOk());
  }

  @Test
  void loginValidatesInput() throws Exception {
    mvc.perform(post("/api/admin/auth/login").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.taskhub.domain.identity.SessionService sessions;

  @Test
  void databaseFailureDuringBearerLookupHasSanitizedEnvelope() throws Exception {
    String token = "a".repeat(43);
    org.mockito.Mockito.when(sessions.authenticate(token))
        .thenThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "private connection details"));
    mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value(50300));
  }
}
