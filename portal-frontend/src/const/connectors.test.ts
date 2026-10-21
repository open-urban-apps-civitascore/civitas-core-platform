import { describe, expect, it } from 'vitest'

import { CONNECTOR_TYPES } from './connectors'

describe('CONNECTOR_TYPES', () => {
  it('offers exactly the MQTT, SQL and HTTP connector types', () => {
    expect(CONNECTOR_TYPES).toEqual({ MQTT: 'MQTT', SQL: 'SQL', HTTP: 'HTTP' })
  })
})
