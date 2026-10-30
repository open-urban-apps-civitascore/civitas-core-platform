/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.resource.DisallowSchemaLoader;
import java.util.List;

/**
 * The validator settings of Model Forge, so that the processor accepts what Model Forge accepts as
 * data of the Data structure.
 */
final class ModelForgeSchemaFactory {

  static final List<String> NON_VALIDATING_KEYWORDS =
      List.of("x-core-ref", "x-xsd-source", "x-ui-position");

  private ModelForgeSchemaFactory() {}

  /**
   * A factory of its own for each schema: a factory caches the documents it resolves by their
   * {@code $id}, and the Pipelines of all tenants share the JVM of NiFi.
   */
  static JsonSchemaFactory create() {
    JsonMetaSchema.Builder metaSchemaBuilder = JsonMetaSchema.builder(JsonMetaSchema.getV202012());
    NON_VALIDATING_KEYWORDS.forEach(
        keyword -> metaSchemaBuilder.keyword(new NonValidationKeyword(keyword)));
    JsonMetaSchema metaSchema = metaSchemaBuilder.build();
    return JsonSchemaFactory.getInstance(
        SpecVersion.VersionFlag.V202012,
        builder ->
            builder
                .metaSchema(metaSchema)
                .defaultMetaSchemaIri(metaSchema.getIri())
                .schemaLoaders(
                    loaders ->
                        loaders
                            .add(new GeoJsonSchemaLoader())
                            .add(DisallowSchemaLoader.getInstance())));
  }
}
