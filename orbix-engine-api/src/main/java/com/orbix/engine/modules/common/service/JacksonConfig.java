package com.orbix.engine.modules.common.service;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Project-wide Jackson tweaks. Currently registers the JSON:API id-as-string
 * rule via {@link IdLongAsStringSerializerModifier} — every Long field whose
 * name is {@code id} or ends in {@code Id} serialises as a JSON string.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer orbixJacksonCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(
            new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
        ));
    }
}
