import { describe, expect, it } from 'vitest'

import { ConnectionTestResult } from '@/types/connectionTest'
import { ConnectorFormToApiSchema, EMPTY_PAGINATION, SECRET_PLACEHOLDER } from '@/types/connectors'

import { hasMaskedSecret, isApiCallIncomplete, toConnectionTestInput, toPreviewText } from './connectionTest'

const MQTT_CONFIGURATION = {
  urls: 'mqtt://broker.example.org:1883',
  topics: 'sensors',
  qos: '1',
  protocol_version: '3',
  connect_timeout: '',
  keepalive: '',
  tls: false,
  user: 'test.user1',
  password: 'secret',
}

const SQL_CONFIGURATION = {
  driver: 'postgres',
  dsn: 'postgres://db.example.org:5432/city',
  table: 'measurements',
  columns: 'id,value',
  where: '',
  user: 'test.user1',
  password: 'secret',
}

const API_CALL = {
  name: '  Get all stations  ',
  path: '/stations',
  queryParameters: [],
  headers: [],
  pagination: EMPTY_PAGINATION,
}

const httpConfiguration = (overrides: Record<string, unknown> = {}) => ({
  baseUrl: 'https://api.example.org',
  authentication: { type: 'NONE', username: '', password: '' },
  apiCalls: [API_CALL],
  ...overrides,
})

const result = (overrides: Partial<ConnectionTestResult> = {}): ConnectionTestResult => ({
  success: true,
  reason: null,
  message: null,
  preview: null,
  previewTruncated: false,
  rowCount: null,
  httpStatus: null,
  ...overrides,
})

describe('toConnectionTestInput', () => {
  it.each([
    ['MQTT', MQTT_CONFIGURATION],
    ['SQL', SQL_CONFIGURATION],
  ] as const)('sends the %s connector without an API call', (connectorType, configuration) => {
    const values = { connectorType, configuration }

    expect(toConnectionTestInput(values)).toEqual({ connector: ConnectorFormToApiSchema.parse(values) })
  })

  it('sends the trimmed name of the first API call for HTTP', () => {
    const values = { connectorType: 'HTTP' as const, configuration: httpConfiguration() }

    expect(toConnectionTestInput(values)).toEqual({
      connector: ConnectorFormToApiSchema.parse(values),
      apiCall: 'Get all stations',
    })
  })

  it.each([
    ['no API call', httpConfiguration({ apiCalls: [] })],
    ['an API call with an empty name', httpConfiguration({ apiCalls: [{ ...API_CALL, name: '  ' }] })],
    ['an API call with an empty path', httpConfiguration({ apiCalls: [{ ...API_CALL, path: '' }] })],
  ])('returns null for HTTP with %s', (_, configuration) => {
    expect(toConnectionTestInput({ connectorType: 'HTTP', configuration })).toBeNull()
  })

  it('returns null for invalid values', () => {
    const configuration = { ...MQTT_CONFIGURATION, urls: 'not a url' }

    expect(toConnectionTestInput({ connectorType: 'MQTT', configuration })).toBeNull()
  })
})

describe('isApiCallIncomplete', () => {
  it.each([
    ['no API call', httpConfiguration({ apiCalls: [] })],
    ['an API call with an empty name', httpConfiguration({ apiCalls: [{ ...API_CALL, name: '  ' }] })],
    ['an API call with an empty path', httpConfiguration({ apiCalls: [{ ...API_CALL, path: '' }] })],
    [
      'a query parameter without a name',
      httpConfiguration({ apiCalls: [{ ...API_CALL, queryParameters: [{ name: '', value: 'x' }] }] }),
    ],
    [
      'an enabled pagination without a page size',
      httpConfiguration({
        apiCalls: [
          {
            ...API_CALL,
            pagination: { ...EMPTY_PAGINATION, isEnabled: true, pageParameter: 'page', pageSizeParameter: 'size' },
          },
        ],
      }),
    ],
  ])('returns true for HTTP with %s', (_, configuration) => {
    expect(isApiCallIncomplete({ connectorType: 'HTTP', configuration })).toBe(true)
  })

  it('returns false for HTTP with a complete API call', () => {
    expect(isApiCallIncomplete({ connectorType: 'HTTP', configuration: httpConfiguration() })).toBe(false)
  })

  it('returns false for HTTP with a complete API call with an invalid path', () => {
    const configuration = httpConfiguration({ apiCalls: [{ ...API_CALL, path: 'stations' }] })

    expect(isApiCallIncomplete({ connectorType: 'HTTP', configuration })).toBe(false)
  })

  it.each([
    ['MQTT', MQTT_CONFIGURATION],
    ['SQL', SQL_CONFIGURATION],
  ] as const)('returns false for %s', (connectorType, configuration) => {
    expect(isApiCallIncomplete({ connectorType, configuration })).toBe(false)
  })
})

describe('hasMaskedSecret', () => {
  it.each([
    ['MQTT', MQTT_CONFIGURATION],
    ['SQL', SQL_CONFIGURATION],
  ] as const)('returns true for a masked %s password', (connectorType, configuration) => {
    expect(hasMaskedSecret({ connectorType, configuration: { ...configuration, password: SECRET_PLACEHOLDER } })).toBe(
      true,
    )
  })

  it('returns true for a masked HTTP password with Basic authentication', () => {
    const authentication = { type: 'BASIC', username: 'test.user1', password: SECRET_PLACEHOLDER }

    expect(hasMaskedSecret({ connectorType: 'HTTP', configuration: httpConfiguration({ authentication }) })).toBe(true)
  })

  it.each([
    ['Bearer token', { type: 'BEARER_TOKEN', token: SECRET_PLACEHOLDER }],
    ['API token', { type: 'API_TOKEN', name: 'X-API-Key', value: SECRET_PLACEHOLDER, in: 'HEADER' }],
    [
      'OAuth2',
      {
        type: 'OAUTH2',
        tokenUrl: 'https://auth.example.org/token',
        clientId: 'client-1',
        clientSecret: SECRET_PLACEHOLDER,
        scope: '',
      },
    ],
    ['client certificate', { type: 'CLIENT_CERTIFICATE', certificate: 'certificate', privateKey: SECRET_PLACEHOLDER }],
  ])('returns true for a masked secret of %s', (_name, authentication) => {
    expect(hasMaskedSecret({ connectorType: 'HTTP', configuration: httpConfiguration({ authentication }) })).toBe(true)
  })

  it('returns false for a masked HTTP password without authentication', () => {
    const authentication = { type: 'NONE', username: '', password: SECRET_PLACEHOLDER }

    expect(hasMaskedSecret({ connectorType: 'HTTP', configuration: httpConfiguration({ authentication }) })).toBe(false)
  })

  it('returns false for a typed password', () => {
    expect(hasMaskedSecret({ connectorType: 'SQL', configuration: SQL_CONFIGURATION })).toBe(false)
  })
})

describe('toPreviewText', () => {
  it.each([null, ''])('returns null for the preview %j', preview => {
    expect(toPreviewText(result({ preview }), 'SQL')).toBeNull()
  })

  it('returns the SQL preview unchanged', () => {
    const preview = 'id,value\n1,20.5\n2,21.0'

    expect(toPreviewText(result({ preview }), 'SQL')).toBe(preview)
  })

  it('returns an HTTP JSON body pretty-printed with 2 spaces', () => {
    expect(toPreviewText(result({ preview: '{"id":1,"tags":["a"]}' }), 'HTTP')).toBe(
      '{\n  "id": 1,\n  "tags": [\n    "a"\n  ]\n}',
    )
  })

  it('returns an HTTP body that is not JSON unchanged', () => {
    expect(toPreviewText(result({ preview: '<html>ok</html>' }), 'HTTP')).toBe('<html>ok</html>')
  })

  it('removes the truncation marker from a truncated preview', () => {
    const preview = 'id,value\n1,20.5\n[truncated]'

    expect(toPreviewText(result({ preview, previewTruncated: true }), 'SQL')).toBe('id,value\n1,20.5')
  })

  it('returns a truncated HTTP JSON body that does not parse as raw text', () => {
    const preview = '{"id":1,"na\n[truncated]'

    expect(toPreviewText(result({ preview, previewTruncated: true }), 'HTTP')).toBe('{"id":1,"na')
  })
})
