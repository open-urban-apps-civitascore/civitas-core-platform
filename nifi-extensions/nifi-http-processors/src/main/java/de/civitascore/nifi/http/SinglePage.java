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

import java.util.List;
import java.util.Optional;

/** An API call without pagination: each run sends one request. */
record SinglePage() implements Pagination {

  private static final Page ONLY = new Page(0, Optional.empty(), List.of(), List.of());

  @Override
  public Page first() {
    return ONLY;
  }

  @Override
  public ResponseShape shape() {
    return new ResponseShape.ObjectOrArray();
  }

  @Override
  public boolean isPastTheEnd(Page page, int status) {
    return false;
  }

  @Override
  public Progress next(Page page, PageContent content) {
    return new Progress.Accepted(content.records(), Optional.empty());
  }
}
