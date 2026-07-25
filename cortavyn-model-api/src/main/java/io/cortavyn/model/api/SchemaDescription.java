package io.cortavyn.model.api;
import java.lang.annotation.*;
/** Human-readable schema documentation for a structured response type or record component. */
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.RECORD_COMPONENT})
public @interface SchemaDescription { String value(); }
