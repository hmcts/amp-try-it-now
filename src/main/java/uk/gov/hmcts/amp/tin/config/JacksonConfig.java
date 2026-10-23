package uk.gov.hmcts.amp.tin.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * Declared rather than relied upon.
     *
     * <p>Recorded bodies are read and written as text, never as a model, so the only Jackson this
     * service does is reading the registry, the fixtures and the stored response headers.
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} stays on: a registry entry with a misspelled field should
     * stop startup, not be silently ignored and leave an API half-configured.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
