package io.cortavyn.model.api;
import java.net.URI;
import java.util.Objects;
/** A document referenced by URI or data URI. */
public record DocumentContent(URI uri, String mediaType, String name) implements ChatContent { public DocumentContent { Objects.requireNonNull(uri, "uri must not be null"); Objects.requireNonNull(mediaType, "mediaType must not be null"); Objects.requireNonNull(name, "name must not be null"); } }
