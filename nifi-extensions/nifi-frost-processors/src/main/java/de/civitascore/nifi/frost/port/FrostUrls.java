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
 * single-valued navigation. There it is the URL itself, or its first segment — see {@link
 * de.civitascore.nifi.frost.batch.SubRequest#reference(String)}.
 *
 * <p>The separators are written as spaces. The URL of a batch sub-request reaches the query parser
 * as it stands: FROST decodes the query string of an HTTP request, but not the URL of a batch item,
 * and the grammar of the parser knows a space as a separator and {@code %20} as three characters of
 * a name.
 */
public final class FrostUrls {

  private static final String AND = " and ";
  private static final String EQ = " eq ";

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

  /**
   * A Location by its reference, among the Locations of one Thing. The Thing is named by the
   * back-reference of its own lookup, so the query cannot reach beyond it.
   *
   * <p>A query on the {@code Locations} collection with {@code thingReference} would not be scoped
   * at all: Locations belong to no project, and a reference is local to its Dataset, so two
   * Datasets that model the same device share it. The second one would find the first one's
   * Location, patch it, and leave its own Thing without one.
   */
  public static String locationLookup(String thingBackReference, String reference) {
    return thingBackReference
        + "/Locations"
        + LOOKUP_OPTIONS
        + filter(term("properties/reference", reference));
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

  /**
   * One Location of the Thing a Datastream belongs to — whether the Thing has a position at all.
   * FROST derives the FeatureOfInterest of a measurement from it when the measurement brings none.
   */
  public static String positionOfThing(String datastreamBackReference) {
    return datastreamBackReference + "/Thing/Locations?$select=id&$top=1";
  }

  /**
   * A single-valued navigation of the entity a back-reference names, answering the identifier
   * alone. The reference stands at the start, because that is the only place FROST reads one in a
   * URL.
   */
  public static String navigation(String reference, String navigation) {
    return reference + "/" + navigation + "?$select=id";
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
