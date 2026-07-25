package io.cortavyn.model.api;
import java.lang.annotation.*;
/** Provider-visible name for a structured response schema. */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface SchemaName { String value(); }
