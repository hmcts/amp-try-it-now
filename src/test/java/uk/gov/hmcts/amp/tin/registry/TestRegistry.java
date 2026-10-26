package uk.gov.hmcts.amp.tin.registry;

import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

/**
 * Builds a loaded {@link ApiRegistry} for tests in other packages.
 *
 * <p>Exists because {@code load()} is package-private - it is a {@code @PostConstruct} hook, not
 * API, and widening it so tests could call it would make a lifecycle method look like something
 * callers may invoke. This fixture lives in the registry package instead, which is where the
 * access actually belongs.
 */
public final class TestRegistry {

    private TestRegistry() {
    }

    /**
     * The real {@code registry/apis.yaml}, loaded in the self-issued realm.
     */
    public static ApiRegistry selfIssued() {
        return load(new MockEnvironment(), "SELF_ISSUED");
    }

    /**
     * The real registry in the Entra realm, with whatever audiences the environment supplies.
     */
    public static ApiRegistry entra(final Environment environment) {
        return load(environment, "ENTRA");
    }

    private static ApiRegistry load(final Environment environment, final String keySource) {
        final ApiRegistry registry = new ApiRegistry(
                new DefaultResourceLoader(), environment, "classpath:registry/apis.yaml", keySource);
        try {
            registry.load();
        } catch (final RuntimeException refused) {
            // Rethrown as-is. A startup refusal carries the reason the deployment is wrong - which
            // variable is unset, and why a blank audience is not survivable - and that message is
            // the thing worth asserting on, so wrapping it here would hide what these tests check.
            throw refused;
        } catch (final Exception unloadable) {
            throw new IllegalStateException("Could not load the registry for a test", unloadable);
        }
        return registry;
    }
}
