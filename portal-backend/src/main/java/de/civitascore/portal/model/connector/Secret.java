package de.civitascore.portal.model.connector;

import java.util.Objects;
import java.util.function.UnaryOperator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * A secret value of a connector configuration. Its JSON form is {@value #PLACEHOLDER} unless {@link
 * ConnectorJson} writes the document for the registry, so a secret cannot reach a response in clear
 * text. A blank value is no secret: {@link #of(String)} returns {@code null} for it.
 *
 * <p>A secret knows where it comes from: a request gives clear text or {@value #PLACEHOLDER}, the
 * registry document gives ciphertext.
 */
@JsonSerialize(using = Secret.Serializer.class)
@JsonDeserialize(using = Secret.Deserializer.class)
public final class Secret {

  public static final String PLACEHOLDER = "********";

  private enum Origin {
    REQUEST,
    REGISTRY
  }

  private final String value;
  private final Origin origin;

  private Secret(String value, Origin origin) {
    this.value = value;
    this.origin = origin;
  }

  /**
   * The secret of {@code value} as a request sends it, or {@code null} when {@code value} is null
   * or blank.
   */
  public static Secret of(String value) {
    return value == null || value.isBlank() ? null : new Secret(value, Origin.REQUEST);
  }

  /** The secret of a ciphertext from the registry document, or {@code null} when it is blank. */
  static Secret stored(String ciphertext) {
    return ciphertext == null || ciphertext.isBlank()
        ? null
        : new Secret(ciphertext, Origin.REGISTRY);
  }

  public boolean isPlaceholder() {
    return origin == Origin.REQUEST && PLACEHOLDER.equals(value);
  }

  boolean isStored() {
    return origin == Origin.REGISTRY;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Secret secret && value.equals(secret.value) && origin == secret.origin;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, origin);
  }

  @Override
  public String toString() {
    return PLACEHOLDER;
  }

  /**
   * Marks a write of the registry document: a stored secret keeps its ciphertext and a clear-text
   * secret is encrypted.
   */
  record StorageWrite(UnaryOperator<String> encrypt) {}

  /** Marks a read of the registry document, whose secrets are ciphertext. */
  record StorageRead() {}

  static final class Serializer extends ValueSerializer<Secret> {
    @Override
    public void serialize(Secret secret, JsonGenerator gen, SerializationContext context) {
      if (!(context.getAttribute(StorageWrite.class) instanceof StorageWrite storage)) {
        gen.writeString(PLACEHOLDER);
      } else if (secret.isStored()) {
        gen.writeString(secret.value);
      } else if (secret.isPlaceholder()) {
        throw new IllegalStateException(
            "A placeholder must be resolved against the stored connector before it is stored");
      } else {
        gen.writeString(storage.encrypt().apply(secret.value));
      }
    }
  }

  static final class Deserializer extends ValueDeserializer<Secret> {
    @Override
    public Secret deserialize(JsonParser parser, DeserializationContext context)
        throws JacksonException {
      if (!parser.hasToken(JsonToken.VALUE_STRING)) {
        return (Secret) context.handleUnexpectedToken(Secret.class, parser);
      }
      return context.getAttribute(StorageRead.class) != null
          ? stored(parser.getString())
          : of(parser.getString());
    }
  }
}
