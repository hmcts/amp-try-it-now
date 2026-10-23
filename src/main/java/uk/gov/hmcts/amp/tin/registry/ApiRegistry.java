package uk.gov.hmcts.amp.tin.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The registry, read once at startup.
 *
 * <p>Every per-API fact the engine needs lives here. Nothing else in the codebase names an
 * individual API, which is what makes adding one a configuration change.
 *
 * <p><b>Fails startup rather than degrading.</b> An API declared without an audience in the Entra
 * realm would accept a token minted for any resource, so a missing value stops the pod instead of
 * quietly widening what the engine will trust.
 */
@Slf4j
@Component
public class ApiRegistry {

    private final ResourceLoader resourceLoader;
    private final Environment environment;
    private final String location;
    private final boolean entraRealm;

    private Map<String, ApiDefinition> byShortCode = Map.of();

    public ApiRegistry(final ResourceLoader resourceLoader,
                       final Environment environment,
                       @Value("${registry.location:classpath:registry/apis.yaml}") final String location,
                       @Value("${auth.key-source:SELF_ISSUED}") final String keySource) {
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.location = location;
        this.entraRealm = "ENTRA".equalsIgnoreCase(keySource);
    }

    @PostConstruct
    void load() throws IOException {
        final Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("No API registry at " + location);
        }

        final RegistryFile file;
        try (InputStream in = resource.getInputStream()) {
            file = new ObjectMapper(new YAMLFactory()).readValue(in, RegistryFile.class);
        }
        if (file.apis() == null || file.apis().isEmpty()) {
            throw new IllegalStateException("The API registry at " + location + " declares no APIs.");
        }

        final Map<String, ApiDefinition> loaded = new LinkedHashMap<>();
        for (final RegistryEntry entry : file.apis()) {
            final String shortCode = require(entry.shortCode(), "shortCode", entry.shortCode());
            final String audience = resolveAudience(entry);
            loaded.put(shortCode.toLowerCase(Locale.ROOT), new ApiDefinition(
                    shortCode,
                    entry.title(),
                    entry.catalogueName(),
                    entry.productId(),
                    require(entry.role(), "role", shortCode),
                    audience,
                    entry.operations() == null ? List.of() : entry.operations()));
        }
        byShortCode = Map.copyOf(loaded);
        log.info("API registry loaded: {} apis {} realm:{}",
                byShortCode.size(), byShortCode.keySet(), entraRealm ? "ENTRA" : "SELF_ISSUED");
    }

    /**
     * The audience this API's tokens must carry.
     *
     * <p>Read from the environment variable the registry names, never written into the registry
     * itself: a resource app's id does not exist until external-entra-id has created it, and it
     * differs per environment.
     */
    private String resolveAudience(final RegistryEntry entry) {
        if (!entraRealm) {
            // Self-issued: the engine signs its own tokens and is its own audience. A per-API
            // resource app id is neither available nor meaningful here.
            return "";
        }
        final String variable = require(entry.audienceEnv(), "audienceEnv", entry.shortCode());
        final String value = environment.getProperty(variable, "");
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "auth.key-source=ENTRA but " + variable + " is not set for api '"
                            + entry.shortCode() + "'. A blank audience would accept a token minted "
                            + "for any resource, so startup stops here rather than widening trust.");
        }
        return value;
    }

    private static String require(final String value, final String field, final String shortCode) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "API registry entry '" + shortCode + "' is missing " + field + ".");
        }
        return value;
    }

    public Optional<ApiDefinition> byShortCode(final String shortCode) {
        return shortCode == null
                ? Optional.empty()
                : Optional.ofNullable(byShortCode.get(shortCode.toLowerCase(Locale.ROOT)));
    }

    public List<ApiDefinition> all() {
        return List.copyOf(byShortCode.values());
    }

    public boolean isRegistered(final String shortCode) {
        return byShortCode(shortCode).isPresent();
    }

    record RegistryFile(List<RegistryEntry> apis) {
    }

    record RegistryEntry(String shortCode, String title, String catalogueName, String productId,
                         String role, String audienceEnv,
                         List<ApiDefinition.Operation> operations) {
    }
}
