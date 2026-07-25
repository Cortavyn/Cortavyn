package io.cortavyn.model.api;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Immutable, provider-neutral description of a model's operational capabilities. */
public record ModelProfile(String providerId, String modelName, Set<ModelCapability> capabilities,
                           @Nullable Integer contextWindowTokens) {
    public ModelProfile {
        providerId = requireNonBlank(providerId, "providerId");
        modelName = requireNonBlank(modelName, "modelName");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        if (contextWindowTokens != null && contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens must be positive");
        }
    }

    /** Returns whether the profile declares the supplied portable capability. */
    public boolean supports(ModelCapability capability) { return capabilities.contains(Objects.requireNonNull(capability, "capability must not be null")); }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
