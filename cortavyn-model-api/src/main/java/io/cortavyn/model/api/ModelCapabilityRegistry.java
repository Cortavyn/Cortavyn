package io.cortavyn.model.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe registry of known provider/model profiles. */
public final class ModelCapabilityRegistry {
    private final Map<Key, ModelProfile> profiles = new LinkedHashMap<>();

    /** Registers or replaces a profile for its provider and model name. */
    public synchronized void register(ModelProfile profile) {
        Objects.requireNonNull(profile, "profile must not be null");
        profiles.put(new Key(profile.providerId(), profile.modelName()), profile);
    }

    /** Finds an exact provider/model profile. */
    public synchronized Optional<ModelProfile> find(String providerId, String modelName) {
        return Optional.ofNullable(profiles.get(new Key(providerId, modelName)));
    }

    /** Returns a stable snapshot of all registered profiles. */
    public synchronized Collection<ModelProfile> profiles() { return java.util.List.copyOf(profiles.values()); }

    private record Key(String providerId, String modelName) {
        private Key {
            Objects.requireNonNull(providerId, "providerId must not be null");
            Objects.requireNonNull(modelName, "modelName must not be null");
        }
    }
}
