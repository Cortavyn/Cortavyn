package io.cortavyn.provider.azureopenai;

/** Indicates that an Azure OpenAI response cannot be represented by the portable chat API. */
public final class AzureOpenAiResponseException extends RuntimeException {
    AzureOpenAiResponseException(String message) { super(message); }
    AzureOpenAiResponseException(String message, Throwable cause) { super(message, cause); }
}
