package io.cortavyn.model.api;

import java.util.Map;
import java.util.Objects;

/** Provider-specific configuration passed to a registered model factory. */
public record ModelConfiguration(String modelName, Map<String, Object> options) {
    public ModelConfiguration {
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (modelName.isBlank()) throw new IllegalArgumentException("modelName must not be blank");
        options = Map.copyOf(Objects.requireNonNull(options, "options must not be null"));
    }
    public ModelConfiguration(String modelName) { this(modelName, Map.of()); }
}
