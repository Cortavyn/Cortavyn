package io.cortavyn.model.api;

/** SPI implemented by optional provider modules to construct chat models. */
public interface ModelProviderFactory {
    /** Stable lowercase identifier, for example {@code openai}. */
    String providerId();
    /** Builds one configured model instance. */
    ChatModel create(ModelConfiguration configuration);
}
