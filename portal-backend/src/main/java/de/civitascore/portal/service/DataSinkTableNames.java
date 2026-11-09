package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the PostGIS table a Data sink writes into, or that the next release drops. */
@Component
@RequiredArgsConstructor
public class DataSinkTableNames {

  private final ModelRegistryGateway modelRegistryGateway;

  /**
   * The table of an active sink lives in its configuration in Model Forge; a sink that waits for
   * removal keeps it on its row.
   *
   * @return the table name, or {@code null} for a sink without a stored table name
   */
  public String of(DataSink sink) {
    if (!sink.isActive()) {
      return sink.getUnprovisionTableName();
    }
    if (sink.getConfigurationUrn() == null) {
      return null;
    }
    return modelRegistryGateway
        .fetchPayload(sink.getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .map(content -> content.get("tableName"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse(null);
  }
}
