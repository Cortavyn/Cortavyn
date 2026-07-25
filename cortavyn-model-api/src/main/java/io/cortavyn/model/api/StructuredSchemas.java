package io.cortavyn.model.api;

import java.lang.reflect.*;
import java.util.*;
import org.jspecify.annotations.Nullable;

final class StructuredSchemas {
    static StructuredOutputSchema fromRecord(Class<?> type) {
        SchemaName name = type.getAnnotation(SchemaName.class);
        SchemaDescription description = type.getAnnotation(SchemaDescription.class);
        Map<String, Object> schema = object(type);
        if (description != null) schema.put("description", description.value());
        return new StructuredOutputSchema(name == null ? type.getSimpleName() : name.value(), schema, true);
    }
    private static Map<String, Object> object(Class<?> type) {
        if (!type.isRecord()) throw new IllegalArgumentException("structured output type must be a record: " + type.getName());
        Map<String, Object> properties = new LinkedHashMap<>(); List<String> required = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Map<String, Object> value = schema(component.getGenericType());
            SchemaDescription description = component.getAnnotation(SchemaDescription.class);
            if (description != null) value.put("description", description.value());
            properties.put(component.getName(), value);
            if (!component.getAnnotatedType().isAnnotationPresent(Nullable.class)) required.add(component.getName());
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("type", "object"); result.put("properties", properties);
        if (!required.isEmpty()) result.put("required", required); result.put("additionalProperties", false); return result;
    }
    private static Map<String, Object> schema(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            if (Iterable.class.isAssignableFrom(raw)) return new LinkedHashMap<>(Map.of("type", "array", "items", schema(parameterized.getActualTypeArguments()[0])));
            if (Map.class.isAssignableFrom(raw)) return new LinkedHashMap<>(Map.of("type", "object", "additionalProperties", schema(parameterized.getActualTypeArguments()[1])));
        }
        if (type instanceof Class<?> value) {
            if (value.isRecord()) return object(value);
            if (value.isEnum()) { List<String> names = new ArrayList<>(); for (Object constant : value.getEnumConstants()) names.add(((Enum<?>) constant).name()); return new LinkedHashMap<>(Map.of("type", "string", "enum", names)); }
            return new LinkedHashMap<>(Map.of("type", kind(value)));
        }
        return new LinkedHashMap<>();
    }
    private static String kind(Class<?> type) { if (type == boolean.class || type == Boolean.class) return "boolean"; if (type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer"; if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number"; return "string"; }
    private StructuredSchemas() { }
}
