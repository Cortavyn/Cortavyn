package io.cortavyn.model.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Central registry and factory for provider-neutral runtime model selection. */
public final class ModelFactory {
    private final Map<String, ModelProviderFactory> providers = new LinkedHashMap<>();
    private final ModelCapabilityRegistry capabilities;

    public ModelFactory(ModelCapabilityRegistry capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
    }

    /** Creates a factory and discovers provider factories visible through {@link ServiceLoader}. */
    public static ModelFactory discover(ModelCapabilityRegistry capabilities) {
        var factory = new ModelFactory(capabilities);
        ServiceLoader.load(ModelProviderFactory.class).forEach(factory::register);
        return factory;
    }

    /** Registers a factory; replacing an existing provider registration is deliberate. */
    public synchronized void register(ModelProviderFactory provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providers.put(provider.providerId(), provider);
    }

    /** Creates a model using the registered provider factory. */
    public synchronized ChatModel create(String providerId, ModelConfiguration configuration) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        var provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("No model provider registered for '" + providerId + "'");
        return provider.create(configuration);
    }

    /** Returns the capability registry used by this factory. */
    public ModelCapabilityRegistry capabilities() { return capabilities; }
}
