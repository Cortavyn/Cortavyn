package io.cortavyn.model.api;
import java.net.URI;
import java.util.Objects;
/** An image referenced by URI or data URI. */
public record ImageContent(URI uri, String mediaType) implements ChatContent { public ImageContent { Objects.requireNonNull(uri, "uri must not be null"); Objects.requireNonNull(mediaType, "mediaType must not be null"); } }
