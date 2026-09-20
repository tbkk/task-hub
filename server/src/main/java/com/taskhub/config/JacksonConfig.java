package com.taskhub.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> builder.serializers(
            new ToStringSerializer(Long.class),
            new ToStringSerializer(Long.TYPE)
        );
    }
}
