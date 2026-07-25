package io.cortavyn.model.api;

/** Raised when a provider cannot represent a requested portable content block. */
public final class UnsupportedChatContentException extends IllegalArgumentException {
    public UnsupportedChatContentException(String provider, ChatContent content) {
        super(provider + " does not support " + content.getClass().getSimpleName());
    }

    public UnsupportedChatContentException(String provider, String detail) {
        super(provider + " does not support " + detail);
    }
}
