/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionTestFactoryTest {

  @Test
  void noneMeansNoDeniedNetworks() {
    assertTrue(ConnectionTestFactory.deniedNetworks("none").isEmpty());
  }

  @Test
  void noneIsRecognisedIgnoringCaseAndWhitespace() {
    assertTrue(ConnectionTestFactory.deniedNetworks(" NONE ").isEmpty());
    assertTrue(ConnectionTestFactory.list(" None ").isEmpty());
  }

  /** The guard is built as the factory builds it from {@code connectiontest.denied-networks}. */
  @Test
  void deniedNetworksAreReadFromAList() {
    DestinationGuard guard =
        new DestinationGuard(
            ConnectionTestFactory.deniedNetworks("10.0.0.0/8, fc00::/7"),
            List.of(),
            InetAddress::getAllByName);

    assertThrows(ConnectionTestFailure.class, () -> guard.check("10.1.2.3"));
    assertThrows(ConnectionTestFailure.class, () -> guard.check("fd00::1"));
    assertDoesNotThrow(() -> guard.check("93.184.216.34"));
  }

  @Test
  void invalidDeniedNetworkIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> ConnectionTestFactory.deniedNetworks("not-a-cidr"));
  }

  @Test
  void listIsTrimmedAndSkipsBlankEntries() {
    assertEquals(
        List.of(".svc.cluster.local", "intern.example"),
        ConnectionTestFactory.list(" .svc.cluster.local , ,intern.example,"));
  }
}
