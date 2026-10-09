package de.civitascore.portal.model.connector;

import java.util.Objects;
import java.util.function.UnaryOperator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonPointer;
import tools.jackson.core.JsonToken;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
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
 * <p>A secret read from a request holds the clear text or {@value #PLACEHOLDER}; a secret read from
 * the registry holds the ciphertext.
 */
@JsonSerialize(using = Secret.Serializer.class)
@JsonDeserialize(using = Secret.Deserializer.class)
public final class Secret {

  public static final String PLACEHOLDER = "********";

  private final String value;

  private Secret(String value) {
    this.value = value;
  }

  /** The secret of {@code value}, or {@code null} when {@code value} is null or blank. */
  public static Secret of(String value) {
    return value == null || value.isBlank() ? null : new Secret(value);
  }

  public boolean isPlaceholder() {
    return PLACEHOLDER.equals(value);
  }

  String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Secret secret && value.equals(secret.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return PLACEHOLDER;
  }

  /**
   * How a secret is written into a registry document: a clear-text secret is encrypted, and a
   * placeholder takes the ciphertext at the same position in {@code storedDocument}.
   */
  record StorageWrite(JsonNode storedDocument, UnaryOperator<String> encrypt) {

    void write(Secret secret, JsonGenerator gen) {
      if (!secret.isPlaceholder()) {
        gen.writeString(encrypt.apply(secret.value()));
        return;
      }
      JsonNode stored = storedDocument.at(positionOfNextValue(gen.streamWriteContext()));
      if (stored.isString() && !stored.stringValue().isBlank()) {
        gen.writeString(stored.stringValue());
      } else {
        gen.writeNull();
      }
    }

    /**
     * The generator counts an array element only once it is written, so the pointer of a value
     * about to be written into an array needs the next index.
     */
    private static JsonPointer positionOfNextValue(TokenStreamContext context) {
      if (!context.inArray()) {
        return context.pathAsPointer();
      }
      int nextIndex = context.hasCurrentIndex() ? context.getCurrentIndex() + 1 : 0;
      return context.getParent().pathAsPointer().appendIndex(nextIndex);
    }
  }

  static final class Serializer extends ValueSerializer<Secret> {
    @Override
    public void serialize(Secret secret, JsonGenerator gen, SerializationContext context) {
      if (context.getAttribute(StorageWrite.class) instanceof StorageWrite storage) {
        storage.write(secret, gen);
      } else {
        gen.writeString(PLACEHOLDER);
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
      return of(parser.getString());
    }
  }
}
