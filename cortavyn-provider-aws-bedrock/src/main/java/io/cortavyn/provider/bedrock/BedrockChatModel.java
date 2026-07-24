package io.cortavyn.provider.bedrock;

import io.cortavyn.model.api.ChatMessage;
import io.cortavyn.model.api.ChatMessageRole;
import io.cortavyn.model.api.ChatModel;
import io.cortavyn.model.api.ChatRequest;
import io.cortavyn.model.api.ChatResponse;
import io.cortavyn.model.api.ChatResponseMetadata;
import io.cortavyn.model.api.TokenUsage;
import io.cortavyn.model.api.ToolCall;
import io.cortavyn.model.api.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * An AWS Bedrock Converse API adapter.
 *
 * <p>It uses the AWS SDK's default credentials and region provider chains unless explicitly
 * configured. This follows LangChain's {@code ChatBedrockConverse} choice of the provider-neutral
 * Converse API rather than individual model invocation formats.</p>
 */
public final class BedrockChatModel implements ChatModel, AutoCloseable {
    private final BedrockRuntimeAsyncClient client;
    private final String modelId;
    private final @Nullable Integer maxTokens;
    private final @Nullable Float temperature;
    private final @Nullable Float topP;
    private final List<String> stopSequences;
    private final @Nullable Document additionalModelRequestFields;
    private final boolean closeClient;

    private BedrockChatModel(Builder builder) {
        modelId = requireNonBlank(builder.modelId, "modelId");
        maxTokens = builder.maxTokens;
        temperature = builder.temperature;
        topP = builder.topP;
        stopSequences = builder.stopSequences == null ? List.of() : List.copyOf(builder.stopSequences);
        additionalModelRequestFields = builder.additionalModelRequestFields;
        closeClient = builder.client == null;
        client = builder.client == null ? createClient(builder.region, builder.credentialsProvider) : builder.client;
        if (maxTokens != null && maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (temperature != null && temperature < 0) throw new IllegalArgumentException("temperature must not be negative");
        if (topP != null && (topP < 0 || topP > 1)) throw new IllegalArgumentException("topP must be in [0.0, 1.0]");
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public CompletionStage<ChatResponse> complete(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return client.converse(toConverseRequest(request)).thenApply(BedrockChatModel::toChatResponse);
    }

    ConverseRequest toConverseRequest(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        List<SystemContentBlock> system = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            ContentBlock content = ContentBlock.builder().text(message.content()).build();
            switch (message.role()) {
                case SYSTEM -> system.add(SystemContentBlock.builder().text(message.content()).build());
                case USER -> messages.add(Message.builder().role(ConversationRole.USER).content(content).build());
                case ASSISTANT -> messages.add(Message.builder().role(ConversationRole.ASSISTANT).content(content).build());
                case TOOL -> messages.add(Message.builder().role(ConversationRole.USER).content(ContentBlock.builder().toolResult(result -> result.toolUseId(message.toolCallId()).content(block -> block.text(message.content()))).build()).build());
            }
        }
        if (messages.isEmpty()) throw new IllegalArgumentException("Bedrock requires at least one non-system message");
        ConverseRequest.Builder builder = ConverseRequest.builder().modelId(modelId).messages(messages);
        if (!system.isEmpty()) builder.system(system);
        if (!request.tools().isEmpty()) {
            List<software.amazon.awssdk.services.bedrockruntime.model.Tool> tools = new ArrayList<>();
            for (ToolDefinition tool : request.tools()) tools.add(software.amazon.awssdk.services.bedrockruntime.model.Tool.builder().toolSpec(spec -> spec.name(tool.name()).description(tool.description()).inputSchema(schema -> schema.json(Document.fromMap(toDocumentMap(tool.inputSchema()))))).build());
            builder.toolConfig(configuration -> configuration.tools(tools));
        }
        if (maxTokens != null || temperature != null || topP != null || !stopSequences.isEmpty()) {
            InferenceConfiguration.Builder inference = InferenceConfiguration.builder();
            if (maxTokens != null) inference.maxTokens(maxTokens);
            if (temperature != null) inference.temperature(temperature);
            if (topP != null) inference.topP(topP);
            if (!stopSequences.isEmpty()) inference.stopSequences(stopSequences);
            builder.inferenceConfig(inference.build());
        }
        if (additionalModelRequestFields != null) builder.additionalModelRequestFields(additionalModelRequestFields);
        return builder.build();
    }

    private static ChatResponse toChatResponse(ConverseResponse response) {
        if (response.output() == null || response.output().message() == null) {
            throw new BedrockResponseException("Bedrock returned no assistant message");
        }
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) content.append(block.text());
            if (block.toolUse() != null) toolCalls.add(new ToolCall(block.toolUse().toolUseId(), block.toolUse().name(), toObjectMap(block.toolUse().input().asMap())));
        }
        if (content.isEmpty() && toolCalls.isEmpty()) throw new BedrockResponseException("Bedrock returned no assistant text or tool call");
        software.amazon.awssdk.services.bedrockruntime.model.TokenUsage usage = response.usage();
        TokenUsage tokenUsage = usage == null ? null : new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
        return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content.toString(), List.of(new io.cortavyn.model.api.TextContent(content.toString())), null, toolCalls), new ChatResponseMetadata(null, response.responseMetadata().requestId(), response.stopReasonAsString(), tokenUsage), java.util.Map.of());
    }

    @Override
    public void close() { if (closeClient) client.close(); }

    private static BedrockRuntimeAsyncClient createClient(@Nullable Region region, @Nullable AwsCredentialsProvider credentialsProvider) {
        BedrockRuntimeAsyncClientBuilder builder = BedrockRuntimeAsyncClient.builder();
        if (region != null) builder.region(region);
        if (credentialsProvider != null) builder.credentialsProvider(credentialsProvider);
        return builder.build();
    }

    private static String requireNonBlank(@Nullable String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
    private static java.util.Map<String, Document> toDocumentMap(java.util.Map<String, Object> values) { return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, entry -> Document.fromString(String.valueOf(entry.getValue())))); }
    private static java.util.Map<String, Object> toObjectMap(java.util.Map<String, Document> values) { return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, entry -> entry.getValue().toString())); }

    public static final class Builder {
        private @Nullable BedrockRuntimeAsyncClient client;
        private @Nullable Region region;
        private @Nullable AwsCredentialsProvider credentialsProvider;
        private @Nullable String modelId;
        private @Nullable Integer maxTokens;
        private @Nullable Float temperature;
        private @Nullable Float topP;
        private @Nullable List<String> stopSequences;
        private @Nullable Document additionalModelRequestFields;
        private Builder() { }
        public Builder client(BedrockRuntimeAsyncClient value) { client = Objects.requireNonNull(value); return this; }
        public Builder region(Region value) { region = Objects.requireNonNull(value); return this; }
        public Builder credentialsProvider(AwsCredentialsProvider value) { credentialsProvider = Objects.requireNonNull(value); return this; }
        public Builder modelId(String value) { modelId = value; return this; }
        public Builder maxTokens(int value) { maxTokens = value; return this; }
        public Builder temperature(float value) { temperature = value; return this; }
        public Builder topP(float value) { topP = value; return this; }
        public Builder stopSequences(List<String> value) { stopSequences = List.copyOf(value); return this; }
        public Builder additionalModelRequestFields(Document value) { additionalModelRequestFields = Objects.requireNonNull(value); return this; }
        public BedrockChatModel build() { return new BedrockChatModel(this); }
    }
}
