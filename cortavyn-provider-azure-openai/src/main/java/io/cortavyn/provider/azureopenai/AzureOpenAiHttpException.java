package io.cortavyn.provider.azureopenai;

/** Indicates that Azure OpenAI returned a non-success HTTP response. */
public final class AzureOpenAiHttpException extends RuntimeException {
    private final int statusCode;

    AzureOpenAiHttpException(int statusCode, String responseBody) {
        super("Azure OpenAI request failed with status " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
