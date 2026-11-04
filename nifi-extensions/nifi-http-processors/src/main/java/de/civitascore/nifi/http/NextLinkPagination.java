/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Follows the next link of each response without putting API host data into a page title. */
record NextLinkPagination(JsonPath nextLinkPath, OptionalInt maxLinks) implements Pagination {

  @Override
  public Page first() {
    return page(0, Optional.empty());
  }

  @Override
  public ResponseShape shape() {
    return new ResponseShape.RecordObject(List.of(nextLinkPath));
  }

  @Override
  public boolean isPastTheEnd(Page page, int status) {
    return false;
  }

  @Override
  public Progress next(Page page, PageContent content) {
    JsonNode value = content.values().get(nextLinkPath);
    if (value.isMissingNode() || value.isNull()) {
      return new Progress.Accepted(content.records(), Optional.empty());
    }
    if (value.isObject()) {
      value = value.path("href");
    }
    if (!value.isTextual()) {
      return new Progress.Rejected(
          "the value at " + nextLinkPath + " is not a string or a link object with a string href");
    }
    if (value.textValue().isEmpty()) {
      return new Progress.Accepted(content.records(), Optional.empty());
    }
    return follow(page, content, value.textValue());
  }

  private Progress follow(Page page, PageContent content, String value) {
    URI link;
    try {
      link = NextLinkUrl.resolve(page.url().orElseThrow(), URI.create(value));
    } catch (IllegalArgumentException e) {
      return new Progress.Rejected("the next link is not a valid URI");
    }
    return new Progress.Accepted(
        content.records(),
        maxLinks.isPresent() && page.index() >= maxLinks.getAsInt()
            ? Optional.empty()
            : Optional.of(page(page.index() + 1, Optional.of(link))));
  }

  @Override
  public List<String> names(ParameterLocation location) {
    return List.of();
  }

  private static Page page(long index, Optional<URI> url) {
    return new Page(
        index, Optional.of(new Page.Position("request", index + 1)), List.of(), List.of(), url);
  }
}
