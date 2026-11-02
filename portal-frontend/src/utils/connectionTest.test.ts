import { describe, expect, it } from 'vitest'

import { ConnectionTestResult, MASKED_SECRET } from '@/types/connectionTest'
import { ConnectorFormToApiSchema, EMPTY_PAGINATION } from '@/types/connectors'

import { hasMaskedSecret, toConnectionTestInput, toPreviewText } from './connectionTest'

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
  ])('sends no API call for HTTP with %s', (_, configuration) => {
    const input = toConnectionTestInput({ connectorType: 'HTTP', configuration })

    expect(input).not.toBeNull()
    expect(input).not.toHaveProperty('apiCall')
  })

  it('returns null for invalid values', () => {
    const configuration = { ...MQTT_CONFIGURATION, urls: 'not a url' }

    expect(toConnectionTestInput({ connectorType: 'MQTT', configuration })).toBeNull()
  })
})

describe('hasMaskedSecret', () => {
  it.each([
    ['MQTT', MQTT_CONFIGURATION],
    ['SQL', SQL_CONFIGURATION],
  ] as const)('returns true for a masked %s password', (connectorType, configuration) => {
    expect(hasMaskedSecret({ connectorType, configuration: { ...configuration, password: MASKED_SECRET } })).toBe(true)
  })

  it('returns true for a masked HTTP password with Basic authentication', () => {
    const authentication = { type: 'BASIC', username: 'test.user1', password: MASKED_SECRET }

    expect(hasMaskedSecret({ connectorType: 'HTTP', configuration: httpConfiguration({ authentication }) })).toBe(true)
  })

  it('returns false for a masked HTTP password without authentication', () => {
    const authentication = { type: 'NONE', username: '', password: MASKED_SECRET }

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
