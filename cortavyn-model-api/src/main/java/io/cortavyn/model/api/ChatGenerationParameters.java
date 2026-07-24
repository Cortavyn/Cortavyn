package io.cortavyn.model.api;
import java.util.List;
import org.jspecify.annotations.Nullable;
/** Portable generation controls; adapters ignore unsupported fields rather than changing semantics. */
public record ChatGenerationParameters(@Nullable Double temperature, @Nullable Double topP, @Nullable Integer maxTokens, List<String> stopSequences, @Nullable Integer seed) { public ChatGenerationParameters { stopSequences = List.copyOf(stopSequences); if (temperature != null && (temperature < 0 || temperature > 2)) throw new IllegalArgumentException("temperature must be in [0, 2]"); if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0, 1]"); if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive"); } public static ChatGenerationParameters defaults() { return new ChatGenerationParameters(null, null, null, List.of(), null); } }
