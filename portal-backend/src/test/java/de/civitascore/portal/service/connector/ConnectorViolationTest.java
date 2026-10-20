package de.civitascore.portal.service.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectorViolationTest {

  @Test
  void at_withKeyPath_prefixesTheConnectorMember() {
    assertThat(ConnectorViolation.at("apiCalls[0].auth", "invalid").path())
        .isEqualTo("connector.apiCalls[0].auth");
  }

  @Test
  void at_withEmptyKeyPath_pointsAtTheConnectorMember() {
    assertThat(ConnectorViolation.at("", "invalid").path()).isEqualTo("connector");
  }
}
