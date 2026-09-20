package com.taskhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JacksonContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serializesBoxedAndPrimitiveLongsAsStrings() throws Exception {
        String json = objectMapper.writeValueAsString(new LongValues(9007199254740993L, 42L));

        assertThat(json).isEqualTo("{\"boxed\":\"9007199254740993\",\"primitive\":\"42\"}");
    }

    private record LongValues(Long boxed, long primitive) {
    }
}
