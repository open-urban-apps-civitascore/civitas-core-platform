package de.civitascore.portal.service.connector;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * The connector of a Data source as the Model Forge registry stores it: a document of kind {@code
 * DATA_SOURCE} with encrypted secrets, pinned by the Data source's {@code configurationUrn}. The
 * logical URN in {@code configurationLogicalUrn} stays across versions. The document names the
 * {@code modelUrn} of the Data source's Data structure version in {@code element}.
 *
 * <p>Every operation reads the stored document at most once. A pin that points at no stored
 * document throws {@link StoredConnectorUnreadableException}.
 */
@Service
@RequiredArgsConstructor
public class DataSourceConnectorStore {

  private static final String FALLBACK_NAME = "datasource";

  private final ModelRegistryGateway modelRegistryGateway;
  private final TextEncryptor textEncryptor;

  /**
   * The stored connector, with its secrets as stored ciphertext.
   *
   * @return the connector, the defaults of its type when none is stored, or null when the Data
   *     source has no connector type
   */
  public ConnectorConfiguration read(DataSource dataSource) {
    if (dataSource.getConnectorType() == null) {
      return null;
    }
    Map<String, Object> document = storedDocument(dataSource);
    return ConnectorJson.readStored(
        dataSource.getConnectorType(), document == null ? Map.of() : document);
  }

  /**
   * The stored connector after a JSON merge patch, as {@link ConnectorJson#patch} defines it.
   * Stores nothing.
   *
   * @param connectorPatch the {@code connector} member of the patch, or a missing node
   * @return the patched connector, or null if the Data source has none afterwards
   * @throws de.civitascore.portal.model.connector.MalformedDataSourceRequestException if the
   *     patched connector cannot be read
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public ConnectorConfiguration patch(DataSource dataSource, JsonNode connectorPatch) {
    return ConnectorJson.patch(read(dataSource), connectorPatch);
  }

  /**
   * Stores {@code connector} and sets the pins on {@code dataSource}: the logical URN when it has
   * none, the versioned URN always. A connector equal to the stored one stores nothing.
   *
   * @param connector the connector to store; null clears the content pin and keeps the logical URN,
   *     so that a later write versions the same artifact
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void write(DataSource dataSource, ConnectorConfiguration connector) {
    if (connector == null) {
      dataSource.setConfigurationUrn(null);
      return;
    }
    Optional<RegistryDocument> stored = fetch(dataSource);
    Map<String, Object> document =
        withElement(
            ConnectorJson.toDocument(
                connector,
                stored.map(RegistryDocument::content).orElse(null),
                textEncryptor::encrypt),
            elementOf(dataSource));
    // Only a connector without a clear-text secret compares equal, because encryption is not
    // deterministic.
    if (stored.isPresent() && modelRegistryGateway.isUnchanged(stored.get(), document, null)) {
      return;
    }
    store(dataSource, document);
  }

  /**
   * Stores a new version of the stored document when its {@code element} does not name the {@code
   * modelUrn} of the Data source's Data structure version, and sets the versioned pin on {@code
   * dataSource}. Does nothing when no connector is stored.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refreshElement(DataSource dataSource) {
    Optional<RegistryDocument> stored = fetch(dataSource);
    if (stored.isEmpty()) {
      return;
    }
    Map<String, Object> content =
        Objects.requireNonNullElse(stored.get().content(), Map.<String, Object>of());
    String element = elementOf(dataSource);
    if (Objects.equals(content.get(ConnectorJson.ELEMENT), element)) {
      return;
    }
    store(dataSource, withElement(content, element));
  }

  private void store(DataSource dataSource, Map<String, Object> document) {
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DATA_SOURCE,
            Optional.ofNullable(dataSource.getConfigurationLogicalUrn()),
            dataSource.getId(),
            dataSource.getName() != null ? dataSource.getName() : FALLBACK_NAME,
            document,
            null);
    if (dataSource.getConfigurationLogicalUrn() == null) {
      dataSource.setConfigurationLogicalUrn(pin.logicalUrn());
    }
    dataSource.setConfigurationUrn(pin.versionedUrn());
  }

  private static String elementOf(DataSource dataSource) {
    DataStructureVersion version = dataSource.getDataStructureVersion();
    return version == null ? null : version.getModelUrn();
  }

  private static Map<String, Object> withElement(Map<String, Object> document, String element) {
    Map<String, Object> copy = new LinkedHashMap<>(document);
    if (element == null) {
      copy.remove(ConnectorJson.ELEMENT);
    } else {
      copy.put(ConnectorJson.ELEMENT, element);
    }
    return copy;
  }

  /**
   * The stored document without the registry's stamps, secrets as ciphertext.
   *
   * @return the document, or null when the Data source has no pin or the stored document is empty
   */
  public Map<String, Object> storedDocument(DataSource dataSource) {
    return fetch(dataSource).map(RegistryDocument::content).orElse(null);
  }

  /** Deletes every stored version. Does nothing when no connector was ever stored. */
  public void delete(DataSource dataSource) {
    if (dataSource.getConfigurationLogicalUrn() != null) {
      modelRegistryGateway.deletePayload(dataSource.getConfigurationLogicalUrn());
    }
  }

  private Optional<RegistryDocument> fetch(DataSource dataSource) {
    String urn = dataSource.getConfigurationUrn();
    if (urn == null) {
      return Optional.empty();
    }
    return Optional.of(
        modelRegistryGateway
            .fetchPayload(urn)
            .orElseThrow(() -> new StoredConnectorUnreadableException(dataSource.getId(), urn)));
  }
}
