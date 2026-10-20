package de.civitascore.portal.model.connector;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A list element of a connector configuration that carries a secret. A {@value Secret#PLACEHOLDER}
 * in the element resolves against the stored element with the same identity, so that reordering,
 * inserting or removing elements cannot give an element the secret of another one.
 */
public interface IdentifiedElement {

  /**
   * The identity of the element, unique within its list and kept across updates by the client. A
   * null identity matches no stored element.
   */
  @JsonIgnore
  Object identity();
}
