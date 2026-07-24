package io.cortavyn.model.api;
import java.net.URI;
import java.util.Objects;
/** Audio referenced by URI or data URI. */
public record AudioContent(URI uri, String mediaType) implements ChatContent { public AudioContent { Objects.requireNonNull(uri, "uri must not be null"); Objects.requireNonNull(mediaType, "mediaType must not be null"); } }
