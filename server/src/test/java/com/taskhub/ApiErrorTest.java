package com.taskhub;

import com.taskhub.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ApiErrorTest {
    @RestController
    static class TestEndpoint {
        @GetMapping("/conflict") Object conflict() { throw new ApiException(409, 40902, "记录已更新，请刷新"); }
        @PostMapping("/body") Object body(@RequestBody java.util.Map<String,Object> input) { return input; }
        @GetMapping("/query") Object query(@RequestParam int page, @RequestParam Boolean enabled) { return page; }
        @GetMapping("/database") Object database() { throw new org.springframework.dao.DataAccessResourceFailureException("internal-db-secret"); }
        @GetMapping("/binding") Object binding(@ModelAttribute Query input) { return input; }
    }
    public static class Query {
        private int page;
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
    }
    @Test void invalidQueryAndBindingUseSafeEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestEndpoint()).setControllerAdvice(new ApiExceptionHandler()).build();
        for (String url : List.of("/query?page=abc&enabled=true", "/query?page=1&enabled=abc", "/query?enabled=true", "/binding?page=abc"))
            mvc.perform(get(url)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数格式不正确"));
    }
    @Test void databaseFailureUsesSafeUnavailableEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestEndpoint()).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/database")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value(50300))
            .andExpect(jsonPath("$.message").value("服务暂不可用，请稍后重试"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-db-secret"))));
    }
    @Test void businessErrorKeepsHttpStatusAndStableEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestEndpoint()).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40902))
            .andExpect(jsonPath("$.message").value("记录已更新，请刷新"));
        mvc.perform(post("/body").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void paginationRejectsUnboundedRequestsAndPreservesNumbers() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageResponse<>(List.of(), 0, 1, 101));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(new PageResponse<>(List.of("id"), 1, 1, 20));
        assertTrue(json.get("total").isNumber());
        assertEquals(1, json.get("total").intValue());
    }
}
