import { describe, expect, it } from 'vitest'

import { CONNECTOR_TYPES } from '@/const/connectors'

import { Datasource, getConnectorIssues, isConnectorReadable } from './datasources'

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
      apiCalls: [{ name: 'Test Call 1', path: '/items', pagination: { type: 'UNKNOWN' } }],
    })

    expect(isConnectorReadable(connector)).toBe(false)
  })

  it('rejects a connector with an unknown authentication', () => {
    expect(isConnectorReadable(httpConnector({ authentication: { type: 'UNKNOWN' } }))).toBe(false)
  })

  it.each([
    { type: 'BEARER_TOKEN', token: '********' },
    { type: 'API_TOKEN', name: 'X-API-Key', value: '********', in: 'HEADER' },
    { type: 'OAUTH2', tokenUrl: 'https://auth.example.org/token', clientId: 'client-1', clientSecret: '********' },
    { type: 'CLIENT_CERTIFICATE', certificate: '********', privateKey: '********' },
  ])('reads a connector with the authentication $type', authentication => {
    const connector = httpConnector({ authentication })

    expect(getConnectorIssues(connector)).toEqual([])
    expect(isConnectorReadable(connector)).toBe(true)
  })
})
