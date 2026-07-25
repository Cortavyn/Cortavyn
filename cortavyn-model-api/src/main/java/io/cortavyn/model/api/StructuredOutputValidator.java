package io.cortavyn.model.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Minimal deterministic validator for the JSON Schema subset emitted by {@link StructuredSchemas}. */
final class StructuredOutputValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    static void validate(Object value, Map<String, Object> schema) {
        List<String> violations = new ArrayList<>(); validate(JSON.valueToTree(value), schema, "$", violations);
        if (!violations.isEmpty()) throw new StructuredOutputException("Response violates structured output schema", violations);
    }
    @SuppressWarnings("unchecked") private static void validate(JsonNode value, Map<String, Object> schema, String path, List<String> violations) {
        if (value.isNull()) return;
        String type = (String) schema.get("type");
        if ("object".equals(type)) {
            if (!value.isObject()) { violations.add(path + " must be an object"); return; }
            Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
            for (Object required : (List<Object>) schema.getOrDefault("required", List.of())) if (!value.has((String) required)) violations.add(path + "." + required + " is required");
            if (Boolean.FALSE.equals(schema.get("additionalProperties"))) value.fieldNames().forEachRemaining(field -> { if (!properties.containsKey(field)) violations.add(path + "." + field + " is not allowed"); });
            for (Map.Entry<String, Object> property : properties.entrySet()) if (value.has(property.getKey())) validate(value.get(property.getKey()), (Map<String, Object>) property.getValue(), path + "." + property.getKey(), violations);
            Object additional = schema.get("additionalProperties"); if (additional instanceof Map<?, ?> additionalSchema) value.fields().forEachRemaining(entry -> validate(entry.getValue(), (Map<String, Object>) additionalSchema, path + "." + entry.getKey(), violations));
        } else if ("array".equals(type)) { if (!value.isArray()) { violations.add(path + " must be an array"); return; } Map<String, Object> items = (Map<String, Object>) Objects.requireNonNull(schema.get("items")); for (int i = 0; i < value.size(); i++) validate(value.get(i), items, path + "[" + i + "]", violations); }
        else if ("string".equals(type) && !value.isTextual()) violations.add(path + " must be a string");
        else if ("integer".equals(type) && !value.isIntegralNumber()) violations.add(path + " must be an integer");
        else if ("number".equals(type) && !value.isNumber()) violations.add(path + " must be a number");
        else if ("boolean".equals(type) && !value.isBoolean()) violations.add(path + " must be a boolean");
        if (schema.containsKey("enum") && value.isTextual() && !((List<String>) schema.get("enum")).contains(value.textValue())) violations.add(path + " must be one of " + schema.get("enum"));
    }
    private StructuredOutputValidator() { }
}
