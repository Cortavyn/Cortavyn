package io.cortavyn.model.api;

import java.net.URI;
import java.util.Base64;

/** Utilities for mapping the portable URI-backed media blocks to provider payloads. */
public final class MediaUris {
    private MediaUris() { }
    public static String base64Data(URI uri, String provider, ChatContent content) {
        String value = uri.toString(); int comma = value.indexOf(',');
        if (!"data".equalsIgnoreCase(uri.getScheme()) || comma < 0 || !value.substring(0, comma).endsWith(";base64")) throw new UnsupportedChatContentException(provider, content.getClass().getSimpleName() + " URI; use a base64 data URI");
        String encoded = value.substring(comma + 1); try { Base64.getDecoder().decode(encoded); return encoded; } catch (IllegalArgumentException exception) { throw new UnsupportedChatContentException(provider, "invalid base64 media data"); }
    }
    public static byte[] decodedBase64Data(URI uri, String provider, ChatContent content) {
        return Base64.getDecoder().decode(base64Data(uri, provider, content));
    }
}
