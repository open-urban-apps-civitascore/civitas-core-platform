/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqlSourceStageTest {

  @Test
  void dsnToJdbcUrlStripsUserinfoWithoutTruncatingAnAtInAQueryParam() {
    // the userinfo separator is only the '@' inside the authority; an '@' inside a query-parameter
    // value must survive, not truncate the URL
    assertEquals(
        "jdbc:postgresql://host:5432/db?applicationname=x@y",
        SqlSourceStage.postgresDsnToJdbcUrl("postgres://u:p@host:5432/db?applicationname=x@y"));
  }
}
