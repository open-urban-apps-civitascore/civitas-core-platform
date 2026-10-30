import { describe, expect, it } from 'vitest'

import { CONNECTOR_TYPES } from '@/const/connectors'

import { Datasource, isConnectorReadable } from './datasources'

const httpConnector = (overrides: Record<string, unknown> = {}) =>
  ({
    connectorType: CONNECTOR_TYPES.HTTP,
    baseUrl: 'https://api.example.com',
    authentication: { type: 'NONE' },
    apiCalls: [{ name: 'Test Call 1', path: '/items', pagination: { type: 'PAGE_NUMBER' } }],
    ...overrides,
  }) as Datasource['connector']

describe('isConnectorReadable', () => {
  it('accepts a connector the frontend can read', () => {
    expect(isConnectorReadable(httpConnector())).toBe(true)
  })

  it('accepts a Data source without a connector', () => {
    expect(isConnectorReadable(null)).toBe(true)
  })

  it('rejects a connector with an unknown pagination type', () => {
    const connector = httpConnector({
      apiCalls: [{ name: 'Test Call 1', path: '/items', pagination: { type: 'LIMIT_OFFSET' } }],
    })

    expect(isConnectorReadable(connector)).toBe(false)
  })

  it('rejects a connector with an unknown authentication', () => {
    expect(isConnectorReadable(httpConnector({ authentication: { type: 'BEARER_TOKEN' } }))).toBe(false)
  })
})
