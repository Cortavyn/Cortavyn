package io.cortavyn.chat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TypedToolSchema {
    private TypedToolSchema() { }

    static Map<String, Object> forRecord(Class<?> argumentsType) {
        if (!argumentsType.isRecord()) {
            throw new IllegalArgumentException("typed tool arguments must be a record: " + argumentsType.getName());
        }
        return objectSchema(argumentsType);
    }

    private static Map<String, Object> objectSchema(Class<?> type) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            properties.put(component.getName(), schema(component.getGenericType()));
            if (component.getAnnotation(ToolDescription.class) != null) {
                Map<String, Object> property = new LinkedHashMap<>((Map<String, Object>) properties.get(component.getName()));
                property.put("description", component.getAnnotation(ToolDescription.class).value());
                properties.put(component.getName(), property);
            }
            if (!component.getAnnotatedType().isAnnotationPresent(Nullable.class)) required.add(component.getName());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> schema(Type type) {
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawType && Iterable.class.isAssignableFrom(rawType)) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", schema(parameterizedType.getActualTypeArguments()[0]));
            return schema;
        }
        if (!(type instanceof Class<?> typeClass)) return Map.of();
        if (typeClass.isRecord()) return objectSchema(typeClass);
        if (typeClass.isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object value : typeClass.getEnumConstants()) values.add(((Enum<?>) value).name());
            return Map.of("type", "string", "enum", values);
        }
        if (typeClass == boolean.class || typeClass == Boolean.class) return Map.of("type", "boolean");
        if (typeClass == byte.class || typeClass == Byte.class || typeClass == short.class || typeClass == Short.class || typeClass == int.class || typeClass == Integer.class || typeClass == long.class || typeClass == Long.class) return Map.of("type", "integer");
        if (typeClass == float.class || typeClass == Float.class || typeClass == double.class || typeClass == Double.class) return Map.of("type", "number");
        return Map.of("type", "string");
    }
}
