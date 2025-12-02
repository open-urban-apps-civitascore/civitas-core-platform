package de.civitascore.portal.model.output.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TopicResolverTest {

  private TopicResolver sut;

  @BeforeEach
  void setUp() {
    sut = new TopicResolver();
  }

  @Test
  @DisplayName("resolve should create topic name from aggregate type and operation")
  void resolveShouldCreateTopicName() {
    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).isEqualTo("User.create");
  }

  @Test
  @DisplayName("resolve should support different aggregate types")
  void resolveShouldSupportDifferentAggregateTypes() {
    // When & Then
    assertThat(sut.resolve("User", "create")).isEqualTo("User.create");
    assertThat(sut.resolve("DataSpace", "update")).isEqualTo("DataSpace.update");
    assertThat(sut.resolve("DataSet", "delete")).isEqualTo("DataSet.delete");
    assertThat(sut.resolve("Group", "create")).isEqualTo("Group.create");
  }

  @Test
  @DisplayName("resolve should support different operations")
  void resolveShouldSupportDifferentOperations() {
    // When & Then
    assertThat(sut.resolve("User", "create")).isEqualTo("User.create");
    assertThat(sut.resolve("User", "update")).isEqualTo("User.update");
    assertThat(sut.resolve("User", "delete")).isEqualTo("User.delete");
  }

  @Test
  @DisplayName("resolve should apply topic prefix when configured")
  void resolveShouldApplyTopicPrefix() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "prod");

    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).isEqualTo("prod.User.create");
  }

  @Test
  @DisplayName("resolve should not apply prefix when it is null")
  void resolveShouldNotApplyNullPrefix() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", null);

    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).isEqualTo("User.create");
  }

  @Test
  @DisplayName("resolve should not apply prefix when it is empty")
  void resolveShouldNotApplyEmptyPrefix() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "");

    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).isEqualTo("User.create");
  }

  @Test
  @DisplayName("resolve should not apply prefix when it is blank")
  void resolveShouldNotApplyBlankPrefix() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "   ");

    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).isEqualTo("User.create");
  }

  @Test
  @DisplayName("resolve should support different environment prefixes")
  void resolveShouldSupportDifferentEnvironmentPrefixes() {
    // Dev environment
    ReflectionTestUtils.setField(sut, "topicPrefix", "dev");
    assertThat(sut.resolve("User", "create")).isEqualTo("dev.User.create");

    // Staging environment
    ReflectionTestUtils.setField(sut, "topicPrefix", "staging");
    assertThat(sut.resolve("User", "create")).isEqualTo("staging.User.create");

    // Production environment
    ReflectionTestUtils.setField(sut, "topicPrefix", "prod");
    assertThat(sut.resolve("User", "create")).isEqualTo("prod.User.create");
  }

  @Test
  @DisplayName("error should return error topic name")
  void errorShouldReturnErrorTopicName() {
    // When
    String errorTopic = sut.error();

    // Then
    assertThat(errorTopic).isEqualTo("events.error");
  }

  @Test
  @DisplayName("error should apply prefix to error topic")
  void errorShouldApplyPrefixToErrorTopic() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "prod");

    // When
    String errorTopic = sut.error();

    // Then
    assertThat(errorTopic).isEqualTo("prod.events.error");
  }

  @Test
  @DisplayName("error should not apply empty prefix to error topic")
  void errorShouldNotApplyEmptyPrefixToErrorTopic() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "");

    // When
    String errorTopic = sut.error();

    // Then
    assertThat(errorTopic).isEqualTo("events.error");
  }

  @Test
  @DisplayName("resolve should throw NullPointerException when aggregateType is null")
  void resolveShouldThrowExceptionWhenAggregateTypeIsNull() {
    // When & Then
    assertThrows(NullPointerException.class, () -> sut.resolve(null, "create"));
  }

  @Test
  @DisplayName("resolve should throw NullPointerException when operation is null")
  void resolveShouldThrowExceptionWhenOperationIsNull() {
    // When & Then
    assertThrows(NullPointerException.class, () -> sut.resolve("User", null));
  }

  @Test
  @DisplayName("resolve should handle aggregate types with multiple words")
  void resolveShouldHandleMultiWordAggregateTypes() {
    // When
    String topic = sut.resolve("DataSpaceAccess", "granted");

    // Then
    assertThat(topic).isEqualTo("DataSpaceAccess.granted");
  }

  @Test
  @DisplayName("resolve should handle custom operations")
  void resolveShouldHandleCustomOperations() {
    // When & Then
    assertThat(sut.resolve("User", "activated")).isEqualTo("User.activated");
    assertThat(sut.resolve("User", "deactivated")).isEqualTo("User.deactivated");
    assertThat(sut.resolve("DataSpace", "shared")).isEqualTo("DataSpace.shared");
  }

  @Test
  @DisplayName("resolve should use dot as delimiter")
  void resolveShouldUseDotAsDelimiter() {
    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).contains(".");
    assertThat(topic.split("\\.")).hasSize(2);
  }

  @Test
  @DisplayName("resolve with prefix should use dot as delimiter")
  void resolveWithPrefixShouldUseDotAsDelimiter() {
    // Given
    ReflectionTestUtils.setField(sut, "topicPrefix", "prod");

    // When
    String topic = sut.resolve("User", "create");

    // Then
    assertThat(topic).contains(".");
    assertThat(topic.split("\\.")).hasSize(3);
    assertThat(topic.split("\\.")[0]).isEqualTo("prod");
    assertThat(topic.split("\\.")[1]).isEqualTo("User");
    assertThat(topic.split("\\.")[2]).isEqualTo("create");
  }
}
