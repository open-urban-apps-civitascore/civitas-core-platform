/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.port;

import de.civitascore.nifi.frost.batch.ODataText;
import java.util.ArrayList;
import java.util.List;

/**
 * The service-relative URLs of the sub-requests.
 *
 * <p>A lookup resolves a reference without a back-reference, which is what the canonical reference
 * block buys: the entity carries {@code thingReference} itself, so the filter is a direct query on
 * the collection and does not depend on a sub-request before it. A back-reference appears only
 * where the identifier of an entity is required, which is the path of a PATCH and of a
 * single-valued navigation.
 *
 * <p>The separators are percent-encoded rather than written as spaces. FROST decodes the URL before
 * it parses the filter, so both forms arrive the same, and the encoded one survives a strict URL
 * parser on the way.
 */
public final class FrostUrls {

  private static final String AND = "%20and%20";
  private static final String EQ = "%20eq%20";

  /**
   * Answers the identifier alone, and only the first entity, which is the one a reference names.
   */
  private static final String LOOKUP_OPTIONS = "?$select=id&$top=1&$filter=";

  private FrostUrls() {}

  /** The Things collection of the dataset's project. */
  public static String things(String projectId) {
    return "Projects(" + projectId + ")/Things";
  }

  /** A Thing by its reference, inside the dataset's project. */
  public static String thingLookup(String projectId, String reference) {
    return things(projectId) + LOOKUP_OPTIONS + filter(term("properties/reference", reference));
  }

  /** A Location by its reference, scoped to its Thing. */
  public static String locationLookup(String projectId, String reference, String thingReference) {
    return "Locations"
        + LOOKUP_OPTIONS
        + filter(
            term("properties/reference", reference),
            term("properties/thingReference", thingReference),
            scope(projectId, "Things/Projects/id"));
  }

  /**
   * A Datastream by its reference, scoped to its Thing and to the dataset's project. Datastreams
   * are not project-scoped themselves, so the project term navigates through the Thing; without it
   * a reference that another Dataset uses too would resolve across Datasets.
   */
  public static String datastreamLookup(String projectId, String reference, String thingReference) {
    return "Datastreams"
        + LOOKUP_OPTIONS
        + filter(
            term("properties/reference", reference),
            term("properties/thingReference", thingReference),
            scope(projectId, "Thing/Projects/id"));
  }

  /** An Observation by its own reference, scoped to its Datastream and to the project. */
  public static String observationLookup(
      String projectId, String reference, String datastreamReference, String thingReference) {
    return "Observations"
        + LOOKUP_OPTIONS
        + filter(
            term("parameters/reference", reference),
            term("Datastream/properties/reference", datastreamReference),
            term("Datastream/properties/thingReference", thingReference),
            scope(projectId, "Datastream/Thing/Projects/id"));
  }

  /** One entity of a collection, addressed by an identifier or by a back-reference to one. */
  public static String entity(String collection, String id) {
    return collection + "(" + id + ")";
  }

  /** A single-valued navigation of one entity, answering the identifier alone. */
  public static String navigation(String collection, String id, String navigation) {
    return entity(collection, id) + "/" + navigation + "?$select=id";
  }

  private static String term(String path, String value) {
    return path + EQ + ODataText.literal(value);
  }

  /**
   * A numeric scope term. The project identifier is validated as numeric before it arrives here.
   */
  private static String scope(String projectId, String path) {
    return path + EQ + projectId;
  }

  private static String filter(String... terms) {
    List<String> conjuncts = new ArrayList<>(terms.length);
    for (String term : terms) {
      conjuncts.add(term);
    }
    return String.join(AND, conjuncts);
  }
}
