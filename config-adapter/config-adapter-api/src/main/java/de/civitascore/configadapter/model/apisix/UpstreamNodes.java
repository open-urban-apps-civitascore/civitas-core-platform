/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper for APISIX upstream nodes that supports both map and array formats.
 *
 * <p>APISIX allows two formats for specifying upstream backend nodes:
 *
 * <p><b>Map format</b> — compact form mapping "host:port" to weight:
 *
 * <pre>{@code
 * {
 *   "backend1:8080": 1,
 *   "backend2:8080": 2
 * }
 * }</pre>
 *
 * <p><b>Array format</b> — detailed form with additional fields per node:
 *
 * <pre>{@code
 * [
 *   {"host": "backend1", "port": 8080, "weight": 1, "priority": 0},
 *   {"host": "backend2", "port": 8080, "weight": 2}
 * ]
 * }</pre>
 *
 * <p>Use {@link #ofMap(Map)} or {@link #ofList(List)} to create instances. The custom Jackson
 * deserializer automatically detects the format from JSON input.
 *
 * @see UpstreamNode
 * @see <a href="https://apisix.apache.org/docs/apisix/admin-api/#upstream">APISIX Upstream API</a>
 */
@JsonDeserialize(using = UpstreamNodes.NodesDeserializer.class)
@JsonSerialize(using = UpstreamNodes.NodesSerializer.class)
public final class UpstreamNodes {

  private final Map<String, Integer> mapNodes;
  private final List<UpstreamNode> listNodes;

  private UpstreamNodes(Map<String, Integer> mapNodes, List<UpstreamNode> listNodes) {
    this.mapNodes = mapNodes;
    this.listNodes = listNodes;
  }

  /**
   * Creates an UpstreamNodes instance from the map format.
   *
   * @param nodes map of "host:port" to weight
   * @return a new UpstreamNodes in map format
   */
  public static UpstreamNodes ofMap(Map<String, Integer> nodes) {
    Objects.requireNonNull(nodes, "nodes must not be null");
    return new UpstreamNodes(Map.copyOf(nodes), null);
  }

  /**
   * Creates an UpstreamNodes instance from the array format.
   *
   * @param nodes list of UpstreamNode entries
   * @return a new UpstreamNodes in array format
   */
  public static UpstreamNodes ofList(List<UpstreamNode> nodes) {
    Objects.requireNonNull(nodes, "nodes must not be null");
    return new UpstreamNodes(null, List.copyOf(nodes));
  }

  /** Returns {@code true} if this instance uses the map format. */
  public boolean isMapFormat() {
    return mapNodes != null;
  }

  /** Returns {@code true} if this instance uses the array format. */
  public boolean isListFormat() {
    return listNodes != null;
  }

  /**
   * Returns the nodes as a map, or {@code null} if this is array format.
   *
   * @return unmodifiable map of "host:port" to weight, or null
   */
  public Map<String, Integer> asMap() {
    return mapNodes;
  }

  /**
   * Returns the nodes as a list, or {@code null} if this is map format.
   *
   * @return unmodifiable list of UpstreamNode, or null
   */
  public List<UpstreamNode> asList() {
    return listNodes;
  }

  /**
   * Returns the nodes in the appropriate format for the APISIX Admin API.
   *
   * @return a Map or List suitable for JSON serialization
   */
  public Object toApiValue() {
    return isMapFormat() ? mapNodes : listNodes;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    UpstreamNodes that = (UpstreamNodes) obj;
    return Objects.equals(this.mapNodes, that.mapNodes)
        && Objects.equals(this.listNodes, that.listNodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mapNodes, listNodes);
  }

  @Override
  public String toString() {
    if (isMapFormat()) {
      return "UpstreamNodes[map=" + mapNodes + "]";
    }
    return "UpstreamNodes[list=" + listNodes + "]";
  }

  /** Jackson deserializer that detects map vs. array format from the JSON token. */
  static final class NodesDeserializer extends JsonDeserializer<UpstreamNodes> {

    @Override
    public UpstreamNodes deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (parser.currentToken() == JsonToken.VALUE_NULL) {
        return null;
      }
      if (parser.currentToken() == JsonToken.START_OBJECT) {
        JavaType mapType =
            context.getTypeFactory().constructMapType(Map.class, String.class, Integer.class);
        Map<String, Integer> map = context.readValue(parser, mapType);
        return UpstreamNodes.ofMap(map);
      } else if (parser.currentToken() == JsonToken.START_ARRAY) {
        JavaType listType =
            context.getTypeFactory().constructCollectionType(List.class, UpstreamNode.class);
        List<UpstreamNode> list = context.readValue(parser, listType);
        return UpstreamNodes.ofList(list);
      }
      return (UpstreamNodes)
          context.handleUnexpectedToken(
              UpstreamNodes.class,
              parser.currentToken(),
              parser,
              "Expected JSON object or array for upstream nodes");
    }
  }

  /** Jackson serializer that writes the map or array format depending on the instance. */
  static final class NodesSerializer extends JsonSerializer<UpstreamNodes> {

    @Override
    public void serialize(UpstreamNodes value, JsonGenerator generator, SerializerProvider provider)
        throws IOException {
      if (value.isMapFormat()) {
        provider.defaultSerializeValue(value.asMap(), generator);
      } else {
        provider.defaultSerializeValue(value.asList(), generator);
      }
    }
  }
}
