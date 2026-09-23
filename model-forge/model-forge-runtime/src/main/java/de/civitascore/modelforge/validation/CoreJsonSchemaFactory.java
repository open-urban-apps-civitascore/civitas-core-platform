package de.civitascore.modelforge.validation;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.resource.DisallowSchemaLoader;

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

    private static JsonMetaSchema coreMetaSchema() {
        return JsonMetaSchema.builder(JsonMetaSchema.getV202012())
            .keyword(new NonValidationKeyword("x-core-ref"))
            .keyword(new NonValidationKeyword("x-xsd-source"))
            .keyword(new NonValidationKeyword("x-ui-position"))
            .build();
    }

    public static JsonSchemaFactory withCoreAnnotations() {
        JsonMetaSchema metaSchema = coreMetaSchema();

        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012, builder ->
            builder.metaSchema(metaSchema)
                .defaultMetaSchemaIri(metaSchema.getIri())
                // Resolution is limited to local pointers, the vendored schemas and classpath
                // meta-schemas. Refs resolve eagerly, so any other http or file reference in a
                // submitted document would otherwise be fetched by the server — outside UrlGuard,
                // untimed and following redirects. Accepting further URL-referenced schemas requires
                // either another vendored copy or a SchemaLoader routed through UrlGuard and the
                // capped fetcher.
                .schemaLoaders(loaders -> loaders.add(new VendoredSchemaLoader())
                    .add(DisallowSchemaLoader.getInstance())));
    }

    /**
     * Compiles the JSON Schema 2020-12 meta-schema, for checking that a submitted document is a
     * conforming schema rather than merely a compilable one.
     *
     * <p>Unlike {@link #withCoreAnnotations()} this factory keeps the library's default loaders, so
     * the meta-schema and its vocabularies resolve from the validator jar; {@code DisallowSchemaLoader}
     * would refuse {@code classpath:draft/2020-12/schema} along with everything else. That costs
     * nothing in reach: here the submitted document is the *instance*, so its {@code $ref} and
     * {@code $schema} values are strings being type-checked, never addresses to dereference.
     */
    public static JsonSchema metaSchemaValidator(SchemaValidatorsConfig config) {
        JsonMetaSchema metaSchema = coreMetaSchema();

        return JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012, builder ->
                builder.metaSchema(metaSchema).defaultMetaSchemaIri(metaSchema.getIri()))
            .getSchema(SchemaLocation.of(SpecVersion.VersionFlag.V202012.getId()), config);
    }
}
