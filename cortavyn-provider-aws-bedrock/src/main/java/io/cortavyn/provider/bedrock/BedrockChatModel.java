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
import io.cortavyn.model.api.ReasoningContent;
import io.cortavyn.model.api.ImageContent;
import io.cortavyn.model.api.DocumentContent;
import io.cortavyn.model.api.MediaUris;
import io.cortavyn.model.api.UnsupportedChatContentException;
import io.cortavyn.model.api.StreamingChatModel;
import io.cortavyn.model.api.ChatStreamEvent;
import io.cortavyn.model.api.ChatTextDelta;
import io.cortavyn.model.api.ChatCompletion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.SubmissionPublisher;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
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
public final class BedrockChatModel implements ChatModel, StreamingChatModel, AutoCloseable {
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

    @Override public Publisher<ChatStreamEvent> stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ConverseRequest source = toConverseRequest(request);
        ConverseStreamRequest wire = ConverseStreamRequest.builder().modelId(source.modelId()).messages(source.messages()).system(source.system()).inferenceConfig(source.inferenceConfig()).toolConfig(source.toolConfig()).additionalModelRequestFields(source.additionalModelRequestFields()).build();
        SubmissionPublisher<ChatStreamEvent> publisher = new SubmissionPublisher<>(); StringBuilder text = new StringBuilder();
        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder().subscriber(new ConverseStreamResponseHandler.Visitor() {
            @Override public void visitContentBlockDelta(software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent event) {
                String delta = event.delta().text(); if (delta != null && !delta.isEmpty()) { text.append(delta); publisher.submit(new ChatTextDelta(delta)); }
            }
        }).onComplete(() -> { publisher.submit(new ChatCompletion(new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, text.toString()), ChatResponseMetadata.empty(), java.util.Map.of()))); publisher.close(); }).onError(publisher::closeExceptionally).build();
        var streamFuture = client.converseStream(wire, handler);
        return publisher;
    }

    ConverseRequest toConverseRequest(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        List<SystemContentBlock> system = new ArrayList<>();
        for (ChatMessage message : request.messages()) {
            switch (message.role()) {
                case SYSTEM -> system.add(SystemContentBlock.builder().text(message.content()).build());
                case USER -> messages.add(Message.builder().role(ConversationRole.USER).content(toBedrockUserContent(message)).build());
                case ASSISTANT -> messages.add(Message.builder().role(ConversationRole.ASSISTANT).content(toBedrockAssistantContent(message, ContentBlock.builder().text(message.content()).build())).build());
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
        List<io.cortavyn.model.api.ChatContent> blocks = new ArrayList<>();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) content.append(block.text());
            if (block.reasoningContent() != null && block.reasoningContent().reasoningText() != null) { String signature = block.reasoningContent().reasoningText().signature(); blocks.add(new ReasoningContent(block.reasoningContent().reasoningText().text(), signature == null ? java.util.Map.of() : java.util.Map.of("signature", signature))); }
            if (block.toolUse() != null) toolCalls.add(new ToolCall(block.toolUse().toolUseId(), block.toolUse().name(), toObjectMap(block.toolUse().input().asMap())));
        }
        if (content.isEmpty() && toolCalls.isEmpty()) throw new BedrockResponseException("Bedrock returned no assistant text or tool call");
        software.amazon.awssdk.services.bedrockruntime.model.TokenUsage usage = response.usage();
        TokenUsage tokenUsage = usage == null ? null : new TokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
        blocks.addFirst(new io.cortavyn.model.api.TextContent(content.toString()));
        return new ChatResponse(new ChatMessage(ChatMessageRole.ASSISTANT, content.toString(), blocks, null, toolCalls), new ChatResponseMetadata(null, response.responseMetadata().requestId(), response.stopReasonAsString(), tokenUsage), java.util.Map.of());
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
    private static List<ContentBlock> toBedrockAssistantContent(ChatMessage message, ContentBlock defaultContent) {
        for (io.cortavyn.model.api.ChatContent block : message.contentBlocks()) {
            if (block instanceof ImageContent || block instanceof DocumentContent) throw new UnsupportedChatContentException("AWS Bedrock assistant messages", block);
        }
        if (message.contentBlocks().stream().noneMatch(io.cortavyn.model.api.ReasoningContent.class::isInstance) && message.toolCalls().isEmpty()) return List.of(defaultContent);
        List<ContentBlock> blocks = new ArrayList<>();
        for (io.cortavyn.model.api.ChatContent block : message.contentBlocks()) {
            if (block instanceof io.cortavyn.model.api.ReasoningContent reasoning) {
                Object signature = reasoning.providerState().get("signature");
                blocks.add(ContentBlock.builder().reasoningContent(reasoningBlock -> reasoningBlock.reasoningText(reasoningText -> { reasoningText.text(reasoning.text()); if (signature instanceof String value && !value.isBlank()) reasoningText.signature(value); })).build());
            } else if (block instanceof io.cortavyn.model.api.TextContent text) blocks.add(ContentBlock.builder().text(text.text()).build());
            else if (!(block instanceof ImageContent) && !(block instanceof DocumentContent)) throw new UnsupportedChatContentException("AWS Bedrock", block);
        }
        for (ToolCall call : message.toolCalls()) blocks.add(ContentBlock.builder().toolUse(toolUse -> toolUse.toolUseId(call.id()).name(call.name()).input(Document.fromMap(toDocumentMap(call.arguments())))).build());
        return blocks.isEmpty() ? List.of(defaultContent) : blocks;
    }
    private static List<ContentBlock> toBedrockUserContent(ChatMessage message) {
        List<ContentBlock> blocks = new ArrayList<>();
        for (io.cortavyn.model.api.ChatContent block : message.contentBlocks()) {
            if (block instanceof io.cortavyn.model.api.TextContent text) blocks.add(ContentBlock.builder().text(text.text()).build());
            else if (block instanceof ImageContent image) blocks.add(ContentBlock.builder().image(value -> value.format(mediaSubtype(image.mediaType(), "AWS Bedrock", image)).source(source -> source.bytes(SdkBytes.fromByteArray(MediaUris.decodedBase64Data(image.uri(), "AWS Bedrock", image))))).build());
            else if (block instanceof DocumentContent document) blocks.add(ContentBlock.builder().document(value -> value.name(document.name()).format(mediaSubtype(document.mediaType(), "AWS Bedrock", document)).source(source -> source.bytes(SdkBytes.fromByteArray(MediaUris.decodedBase64Data(document.uri(), "AWS Bedrock", document))))).build());
            else if (!(block instanceof ReasoningContent)) throw new UnsupportedChatContentException("AWS Bedrock", block);
        }
        return blocks.isEmpty() ? List.of(ContentBlock.builder().text(message.content()).build()) : blocks;
    }
    private static String mediaSubtype(String mediaType, String provider, io.cortavyn.model.api.ChatContent content) {
        int slash = mediaType.indexOf('/');
        if (slash < 1 || slash == mediaType.length() - 1) throw new UnsupportedChatContentException(provider, content.getClass().getSimpleName() + " media type");
        return mediaType.substring(slash + 1);
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
