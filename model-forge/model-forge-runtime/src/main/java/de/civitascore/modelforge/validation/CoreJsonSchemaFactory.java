package de.civitascore.modelforge.validation;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SpecVersion;

/**
 * Creates JSON Schema factories with the CORE schema extensions registered
 * as non-validating annotation keywords ({@code x-core-ref}, {@code x-xsd-source},
 * {@code x-ui-position}).
 *
 * <p>{@code x-core-ref} is a typed foreign-key annotation: it declares that a string field
 * holds the CORE URN of another artifact (always global). Reference existence is the concern
 * of registry-aware services, not of pure JSON Schema validation, so the keyword carries no
 * validator here — the annotated field only constrains the URN string shape (e.g. {@code pattern}).
 */
public final class CoreJsonSchemaFactory {

    private CoreJsonSchemaFactory() {
    }

    public static JsonSchemaFactory withCoreAnnotations() {
        JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
            .keyword(new NonValidationKeyword("x-core-ref"))
            .keyword(new NonValidationKeyword("x-xsd-source"))
            .keyword(new NonValidationKeyword("x-ui-position"))
            .build();

        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder ->
            builder.metaSchema(metaSchema).defaultMetaSchemaIri(metaSchema.getIri()));
    }
}
