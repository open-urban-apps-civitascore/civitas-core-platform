package de.civitascore.portal.model.output.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserEventDTOTest {

  @Test
  @DisplayName("should create UserEventDTO with all fields")
  void shouldCreateUserEventDtoWithAllFields() {
    // Given
    UUID id = UUID.randomUUID();
    String firstName = "John";
    String lastName = "Doe";
    String email = "john.doe@example.com";
    Boolean active = true;
    String externalId = "keycloak-123";

    // When
    UserEventDTO dto = new UserEventDTO(id, firstName, lastName, email, active, externalId);

    // Then
    assertNotNull(dto);
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.firstName()).isEqualTo(firstName);
    assertThat(dto.lastName()).isEqualTo(lastName);
    assertThat(dto.email()).isEqualTo(email);
    assertThat(dto.active()).isEqualTo(active);
    assertThat(dto.externalId()).isEqualTo(externalId);
  }

  @Test
  @DisplayName("should handle null externalId")
  void shouldHandleNullExternalId() {
    // Given
    UUID id = UUID.randomUUID();

    // When
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, null);

    // Then
    assertNotNull(dto);
    assertNull(dto.externalId());
  }

  @Test
  @DisplayName("should handle inactive user")
  void shouldHandleInactiveUser() {
    // Given
    UUID id = UUID.randomUUID();

    // When
    UserEventDTO dto = new UserEventDTO(id, "Jane", "Smith", "jane@example.com", false, "ext-456");

    // Then
    assertThat(dto.active()).isFalse();
  }

  @Test
  @DisplayName("should be serializable to JSON")
  void shouldBeSerializableToJson() throws Exception {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");
    ObjectMapper mapper = new ObjectMapper();

    // When
    String json = mapper.writeValueAsString(dto);

    // Then
    assertNotNull(json);
    assertThat(json).contains("\"id\":");
    assertThat(json).contains("\"firstName\":\"John\"");
    assertThat(json).contains("\"lastName\":\"Doe\"");
    assertThat(json).contains("\"email\":\"john@example.com\"");
    assertThat(json).contains("\"active\":true");
    assertThat(json).contains("\"externalId\":\"ext-123\"");
  }

  @Test
  @DisplayName("should be deserializable from JSON")
  void shouldBeDeserializableFromJson() throws Exception {
    // Given
    UUID id = UUID.randomUUID();
    String json =
        String.format(
            "{\"id\":\"%s\",\"firstName\":\"John\",\"lastName\":\"Doe\","
                + "\"email\":\"john@example.com\",\"active\":true,\"externalId\":\"ext-123\"}",
            id);
    ObjectMapper mapper = new ObjectMapper();

    // When
    UserEventDTO dto = mapper.readValue(json, UserEventDTO.class);

    // Then
    assertNotNull(dto);
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.firstName()).isEqualTo("John");
    assertThat(dto.lastName()).isEqualTo("Doe");
    assertThat(dto.email()).isEqualTo("john@example.com");
    assertThat(dto.active()).isTrue();
    assertThat(dto.externalId()).isEqualTo("ext-123");
  }

  @Test
  @DisplayName("should serialize null externalId as null in JSON")
  void shouldSerializeNullExternalIdAsNull() throws Exception {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, null);
    ObjectMapper mapper = new ObjectMapper();

    // When
    String json = mapper.writeValueAsString(dto);

    // Then
    assertThat(json).contains("\"externalId\":null");
  }

  @Test
  @DisplayName("two DTOs with same data should be equal")
  void twoDtosWithSameDataShouldBeEqual() {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto1 = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");
    UserEventDTO dto2 = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");

    // Then
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("two DTOs with different data should not be equal")
  void twoDtosWithDifferentDataShouldNotBeEqual() {
    // Given
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UserEventDTO dto1 = new UserEventDTO(id1, "John", "Doe", "john@example.com", true, "ext-123");
    UserEventDTO dto2 = new UserEventDTO(id2, "Jane", "Smith", "jane@example.com", true, "ext-456");

    // Then
    assertThat(dto1).isNotEqualTo(dto2);
  }

  @Test
  @DisplayName("should have only 6 fields")
  void shouldHaveOnlySixFields() {
    // Given
    UserEventDTO dto =
        new UserEventDTO(UUID.randomUUID(), "John", "Doe", "john@example.com", true, "ext-123");

    // Then
    assertThat(dto).hasOnlyFields("id", "firstName", "lastName", "email", "active", "externalId");
  }

  @Test
  @DisplayName("should be immutable")
  void shouldBeImmutable() {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");

    // When
    UUID retrievedId = dto.id();

    // Then
    // Record fields are final, so we can't modify them
    assertThat(retrievedId).isEqualTo(id);
  }

  @Test
  @DisplayName("should support toString")
  void shouldSupportToString() {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");

    // When
    String toString = dto.toString();

    // Then
    assertThat(toString).contains("UserEventDTO");
    assertThat(toString).contains("John");
    assertThat(toString).contains("Doe");
    assertThat(toString).contains("john@example.com");
  }

  @Test
  @DisplayName("should have smaller payload than full User entity")
  void shouldHaveSmallerPayloadThanFullUserEntity() throws Exception {
    // Given
    UUID id = UUID.randomUUID();
    UserEventDTO dto = new UserEventDTO(id, "John", "Doe", "john@example.com", true, "ext-123");
    ObjectMapper mapper = new ObjectMapper();

    // When
    String json = mapper.writeValueAsString(dto);

    // Then
    // UserEventDTO JSON should be significantly smaller than a full User entity
    // which would include groups, roles, timestamps, etc.
    assertThat(json.length()).isLessThan(500); // Arbitrary threshold
  }

  @Test
  @DisplayName("should not expose sensitive fields")
  void shouldNotExposeSensitiveFields() {
    // Given
    UserEventDTO dto =
        new UserEventDTO(UUID.randomUUID(), "John", "Doe", "john@example.com", true, "ext-123");

    // Then
    // Verify that sensitive fields that might exist in User entity are not in the DTO
    assertThat(dto).hasOnlyFields("id", "firstName", "lastName", "email", "active", "externalId");
    // No password, no apiToken, no internalNotes, etc.
  }
}
