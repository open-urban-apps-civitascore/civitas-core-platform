package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.MqttConnectorConfiguration;
import de.civitascore.portal.model.connector.Secret;
import de.civitascore.portal.model.connector.SqlConnectorConfiguration;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DataSourceConnectorStoreTest {

  private static final String LOGICAL_URN =
      "urn:core:platform:civitas:datasource:common:00000000-0000-4000-8000-000000000014";
  private static final String VERSIONED_URN = LOGICAL_URN + ":1.0.0";
  private static final String NEXT_VERSIONED_URN = LOGICAL_URN + ":1.1.0";
  private static final String MODEL_URN =
      "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000021:1.0.0";
  private static final String OTHER_MODEL_URN =
      "urn:core:platform:civitas:element:common:00000000-0000-4000-8000-000000000021:2.0.0";

  @Mock private ModelRegistryGateway modelRegistryGateway;

  private DataSourceConnectorStore store() {
    return new DataSourceConnectorStore(modelRegistryGateway, new PrefixEncryptor());
  }

  private static final class PrefixEncryptor implements TextEncryptor {
    @Override
    public String encrypt(String text) {
      return "ENC(" + text + ")";
    }

    @Override
    public String decrypt(String encryptedText) {
      throw new UnsupportedOperationException();
    }
  }

  private static DataSource dataSource(ConnectorType type) {
    DataSource dataSource = new DataSource();
    dataSource.setId(UUID.randomUUID());
    dataSource.setName("source");
    dataSource.setConnectorType(type);
    return dataSource;
  }

  private DataSource storedSqlSource(String storedPassword) {
    DataSource dataSource = dataSource(ConnectorType.SQL);
    dataSource.setConfigurationLogicalUrn(LOGICAL_URN);
    dataSource.setConfigurationUrn(VERSIONED_URN);
    Map<String, Object> stored = new HashMap<>();
    stored.put("connectionType", "sql");
    stored.put("driver", "postgres");
    stored.put("dsn", "postgres://host/db");
    stored.put("password", storedPassword);
    when(modelRegistryGateway.fetchPayload(VERSIONED_URN))
        .thenReturn(Optional.of(new RegistryDocument(stored, null)));
    return dataSource;
  }

  private static DataStructureVersion structureVersion(String modelUrn) {
    DataStructureVersion version = new DataStructureVersion();
    version.setModelUrn(modelUrn);
    return version;
  }

  /**
   * Makes the document of the last store the stored one, and compares documents as the registry
   * does for documents without registry stamps.
   */
  private void keepWrittenDocumentAsStored() {
    Map<String, Object> written = writtenDocument();
    when(modelRegistryGateway.fetchPayload(NEXT_VERSIONED_URN))
        .thenReturn(Optional.of(new RegistryDocument(written, null)));
    when(modelRegistryGateway.isUnchanged(any(RegistryDocument.class), any(), any()))
        .thenAnswer(
            invocation ->
                invocation
                    .<RegistryDocument>getArgument(0)
                    .content()
                    .equals(invocation.getArgument(1)));
    clearInvocations(modelRegistryGateway);
  }

  private static SqlConnectorConfiguration sqlConnector(String password) {
    return new SqlConnectorConfiguration(
        "postgres", "postgres://host/db", null, null, null, null, Secret.of(password));
  }

  private void stubStore() {
    when(modelRegistryGateway.storePayload(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ModelPin(LOGICAL_URN, NEXT_VERSIONED_URN, "1.1.0"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> writtenDocument() {
    ArgumentCaptor<Map<String, Object>> document = ArgumentCaptor.forClass(Map.class);
    verify(modelRegistryGateway)
        .storePayload(eq(PayloadKind.DATA_SOURCE), any(), any(), any(), document.capture(), any());
    return document.getValue();
  }

  @Nested
  class Read {

    @Test
    void read_withoutConnectorType_returnsNull() {
      assertThat(store().read(dataSource(null))).isNull();
    }

    @Test
    void read_withoutPin_returnsEmptyConnectorOfTheType() {
      ConnectorConfiguration connector = store().read(dataSource(ConnectorType.MQTT));

      assertThat(connector).isInstanceOf(MqttConnectorConfiguration.class);
      assertThat(((MqttConnectorConfiguration) connector).urls()).isNull();
    }

    @Test
    void read_withPin_returnsStoredConnector() {
      SqlConnectorConfiguration connector =
          (SqlConnectorConfiguration) store().read(storedSqlSource("ENC(stored)"));

      assertThat(connector.driver()).isEqualTo("postgres");
      assertThat(connector.password()).isNotNull();
    }
  }

  @Nested
  class Write {

    @Test
    void write_firstConnector_storesEncryptedDocumentAndSetsBothPins() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      stubStore();

      store().write(dataSource, sqlConnector("secret"));

      assertThat(writtenDocument())
          .containsEntry("connectionType", "sql")
          .containsEntry("password", "ENC(secret)")
          .doesNotContainKey("connectorType");
      verify(modelRegistryGateway)
          .storePayload(any(), eq(Optional.empty()), eq(dataSource.getId()), any(), any(), any());
      assertThat(dataSource.getConfigurationLogicalUrn()).isEqualTo(LOGICAL_URN);
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(NEXT_VERSIONED_URN);
    }

    @Test
    void write_withPlaceholderPassword_keepsStoredCiphertext() {
      DataSource dataSource = storedSqlSource("ENC(stored)");
      stubStore();

      store().write(dataSource, sqlConnector(Secret.PLACEHOLDER));

      assertThat(writtenDocument()).containsEntry("password", "ENC(stored)");
      verify(modelRegistryGateway)
          .storePayload(any(), eq(Optional.of(LOGICAL_URN)), any(), any(), any(), any());
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(NEXT_VERSIONED_URN);
    }

    @Test
    void write_withPlaceholderPasswordAndNoStoredPassword_storesNoPassword() {
      DataSource dataSource = storedSqlSource(null);
      stubStore();

      store().write(dataSource, sqlConnector(Secret.PLACEHOLDER));

      assertThat(writtenDocument()).containsEntry("password", null);
    }

    @Test
    void write_withNewPassword_storesItEncrypted() {
      DataSource dataSource = storedSqlSource("ENC(old)");
      stubStore();

      store().write(dataSource, sqlConnector("newpass"));

      assertThat(writtenDocument()).containsEntry("password", "ENC(newpass)");
    }

    @Test
    void write_unchangedConnector_storesNothingAndReadsTheStoredDocumentOnce() {
      DataSource dataSource = storedSqlSource("ENC(stored)");
      when(modelRegistryGateway.isUnchanged(any(RegistryDocument.class), any(), any()))
          .thenReturn(true);

      store().write(dataSource, sqlConnector(Secret.PLACEHOLDER));

      verify(modelRegistryGateway).fetchPayload(VERSIONED_URN);
      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(VERSIONED_URN);
    }

    @Test
    void write_namesTheModelUrnOfTheDataStructureVersionInElement() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));
      stubStore();

      store().write(dataSource, sqlConnector("secret"));

      assertThat(writtenDocument()).containsEntry("element", MODEL_URN);
    }

    @Test
    void write_withoutDataStructureVersion_storesNoElement() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      stubStore();

      store().write(dataSource, sqlConnector("secret"));

      assertThat(writtenDocument()).doesNotContainKey("element");
    }

    @Test
    void write_withDataStructureVersionWithoutModelUrn_storesNoElement() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setDataStructureVersion(structureVersion(null));
      stubStore();

      store().write(dataSource, sqlConnector("secret"));

      assertThat(writtenDocument()).doesNotContainKey("element");
    }

    @Test
    void write_afterTheDataStructureVersionChanged_storesANewVersionWithTheNewElement() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));
      stubStore();
      store().write(dataSource, sqlConnector("secret"));
      keepWrittenDocumentAsStored();
      dataSource.setDataStructureVersion(structureVersion(OTHER_MODEL_URN));

      store().write(dataSource, sqlConnector(Secret.PLACEHOLDER));

      assertThat(writtenDocument()).containsEntry("element", OTHER_MODEL_URN);
    }

    @Test
    void write_sameConnectorAndSameDataStructureVersion_storesNothing() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));
      stubStore();
      store().write(dataSource, sqlConnector("secret"));
      keepWrittenDocumentAsStored();

      store().write(dataSource, sqlConnector(Secret.PLACEHOLDER));

      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(NEXT_VERSIONED_URN);
    }

    @Test
    void write_null_clearsTheContentPinAndKeepsTheLogicalUrn() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setConfigurationLogicalUrn(LOGICAL_URN);
      dataSource.setConfigurationUrn(VERSIONED_URN);

      store().write(dataSource, null);

      assertThat(dataSource.getConfigurationUrn()).isNull();
      assertThat(dataSource.getConfigurationLogicalUrn()).isEqualTo(LOGICAL_URN);
      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class RefreshElement {

    @Test
    void refreshElement_withAnotherElement_storesTheStoredContentWithTheModelUrn() {
      DataSource dataSource = storedSqlSource("ENC(stored)");
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));
      stubStore();

      store().refreshElement(dataSource);

      assertThat(writtenDocument())
          .containsEntry("element", MODEL_URN)
          .containsEntry("password", "ENC(stored)")
          .containsEntry("dsn", "postgres://host/db");
      verify(modelRegistryGateway).fetchPayload(VERSIONED_URN);
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(NEXT_VERSIONED_URN);
    }

    @Test
    void refreshElement_withoutDataStructureVersion_removesTheStoredElement() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setConfigurationLogicalUrn(LOGICAL_URN);
      dataSource.setConfigurationUrn(VERSIONED_URN);
      when(modelRegistryGateway.fetchPayload(VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new RegistryDocument(
                      Map.of("connectionType", "sql", "element", MODEL_URN), null)));
      stubStore();

      store().refreshElement(dataSource);

      assertThat(writtenDocument()).doesNotContainKey("element");
    }

    @Test
    void refreshElement_withTheSameElement_storesNothing() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setConfigurationLogicalUrn(LOGICAL_URN);
      dataSource.setConfigurationUrn(VERSIONED_URN);
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));
      when(modelRegistryGateway.fetchPayload(VERSIONED_URN))
          .thenReturn(
              Optional.of(
                  new RegistryDocument(
                      Map.of("connectionType", "sql", "element", MODEL_URN), null)));

      store().refreshElement(dataSource);

      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
      assertThat(dataSource.getConfigurationUrn()).isEqualTo(VERSIONED_URN);
    }

    @Test
    void refreshElement_withoutStoredConnector_doesNothing() {
      DataSource dataSource = dataSource(ConnectorType.SQL);
      dataSource.setDataStructureVersion(structureVersion(MODEL_URN));

      store().refreshElement(dataSource);

      verify(modelRegistryGateway, never()).fetchPayload(any());
      verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
    }
  }

  @Test
  void patch_mergesOverTheStoredConnectorAndReadsItOnce() {
    DataSource dataSource = storedSqlSource("ENC(stored)");

    SqlConnectorConfiguration patched =
        (SqlConnectorConfiguration)
            store().patch(dataSource, new JsonMapper().readTree("{\"table\":\"users\"}"));

    assertThat(patched.table()).isEqualTo("users");
    assertThat(patched.driver()).isEqualTo("postgres");
    verify(modelRegistryGateway).fetchPayload(VERSIONED_URN);
  }

  @Test
  void storedDocument_withoutPin_isNull() {
    assertThat(store().storedDocument(dataSource(ConnectorType.MQTT))).isNull();
  }

  @Test
  void storedDocument_withPin_isTheStoredCiphertext() {
    assertThat(store().storedDocument(storedSqlSource("ENC(stored)")))
        .containsEntry("password", "ENC(stored)");
  }

  @Test
  void delete_removesEveryVersionBehindTheLogicalUrn() {
    DataSource dataSource = dataSource(ConnectorType.SQL);
    dataSource.setConfigurationLogicalUrn(LOGICAL_URN);

    store().delete(dataSource);

    verify(modelRegistryGateway).deletePayload(LOGICAL_URN);
  }

  @Test
  void delete_withoutStoredConnector_doesNothing() {
    store().delete(dataSource(ConnectorType.SQL));

    verify(modelRegistryGateway, never()).deletePayload(any());
  }

  static Stream<Arguments> everyOperationThatReadsTheStoredDocument() {
    return Stream.of(
        Arguments.of("read", (Operation) DataSourceConnectorStore::read),
        Arguments.of(
            "write",
            (Operation)
                (store, dataSource) -> {
                  store.write(dataSource, sqlConnector("secret"));
                  return null;
                }),
        Arguments.of(
            "patch",
            (Operation)
                (store, dataSource) ->
                    store.patch(dataSource, new JsonMapper().readTree("{\"table\":\"users\"}"))),
        Arguments.of("storedDocument", (Operation) DataSourceConnectorStore::storedDocument),
        Arguments.of(
            "refreshElement",
            (Operation)
                (store, dataSource) -> {
                  store.refreshElement(dataSource);
                  return null;
                }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyOperationThatReadsTheStoredDocument")
  void pinWithoutStoredDocument_throwsUnreadable(String name, Operation operation) {
    DataSource dataSource = dataSource(ConnectorType.SQL);
    dataSource.setConfigurationUrn(VERSIONED_URN);
    when(modelRegistryGateway.fetchPayload(VERSIONED_URN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> operation.apply(store(), dataSource))
        .isInstanceOf(StoredConnectorUnreadableException.class)
        .hasMessageContaining(dataSource.getId().toString())
        .hasMessageContaining(VERSIONED_URN);
    verify(modelRegistryGateway, never()).storePayload(any(), any(), any(), any(), any(), any());
  }

  @FunctionalInterface
  interface Operation {
    Object apply(DataSourceConnectorStore store, DataSource dataSource);
  }
}
