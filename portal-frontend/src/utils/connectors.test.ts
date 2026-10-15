import { CONNECTOR_TYPES } from '@/const/connectors'

import { buildApiCallUrl, formatPathWithQuery, getConnectorDefaults, getConnectorFormData } from './connectors'

vi.mock('@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources', () => ({
  CONNECTOR_INPUTS: {
    MQTT: [
      { key: 'urls', defaultValue: '' },
      { key: 'topics', defaultValue: '' },
      { key: 'connect_timeout', defaultValue: '3s' },
    ],
    SQL: [
      { key: 'columns', defaultValue: '' },
      { key: 'init_files', defaultValue: '' },
    ],
    HTTP: [
      { key: 'baseUrl', defaultValue: '' },
      { key: 'authentication.type', defaultValue: 'NONE' },
      { key: 'authentication.username', defaultValue: '' },
    ],
  },
}))

describe('getConnectorDefaults', () => {
  it('returns mqtt defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.MQTT)
    expect(result).toEqual({
      urls: '',
      topics: '',
      connect_timeout: '3s',
    })
  })

  it('returns sql defaults', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.SQL)
    expect(result).toEqual({
      columns: '',
      init_files: '',
    })
  })

  it('builds nested defaults for dot-path keys', () => {
    const result = getConnectorDefaults(CONNECTOR_TYPES.HTTP)
    expect(result).toEqual({
      baseUrl: '',
      authentication: { type: 'NONE', username: '' },
    })
  })
})

describe('getConnectorFormData', () => {
  it('merges defaults with existing config when provided', () => {
    const existingConfig = { urls: 'a', topics: 'b' }

    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, existingConfig)

    expect(result).toEqual({
      urls: 'a',
      topics: 'b',
      connect_timeout: '3s',
    })
  })

  it('creates defaults when no existing config', () => {
    const result = getConnectorFormData(CONNECTOR_TYPES.MQTT, undefined)

    expect(result).toEqual({
      urls: '',
      topics: '',
      connect_timeout: '3s',
    })
  })
})

describe('formatPathWithQuery', () => {
  it('returns the path without query parameters', () => {
    expect(formatPathWithQuery('/stations', [])).toBe('/stations')
  })

  it('appends the query parameters', () => {
    const queryParameters = [
      { name: 'status', value: 'active' },
      { name: 'limit', value: '10' },
    ]
    expect(formatPathWithQuery('/stations', queryParameters)).toBe('/stations?status=active&limit=10')
  })

  it('leaves out query parameters without a name', () => {
    const queryParameters = [
      { name: '', value: 'x' },
      { name: 'debug', value: '' },
    ]
    expect(formatPathWithQuery('/stations', queryParameters)).toBe('/stations?debug=')
  })
})

describe('buildApiCallUrl', () => {
  const queryParameters = [{ name: 'status', value: 'active' }]

  it('joins host, path and query parameters', () => {
    expect(buildApiCallUrl('https://api.example.org/v1', '/stations', queryParameters)).toBe(
      'https://api.example.org/v1/stations?status=active',
    )
  })

  it('removes the trailing slash of the host', () => {
    expect(buildApiCallUrl('https://api.example.org/v1/', '/stations', [])).toBe('https://api.example.org/v1/stations')
  })

  it('encodes names and values of query parameters', () => {
    const specialParameters = [{ name: 'q a', value: 'a&b #c' }]
    expect(buildApiCallUrl('https://api.example.org', '/search', specialParameters)).toBe(
      'https://api.example.org/search?q%20a=a%26b%20%23c',
    )
  })

  it('leaves out query parameters without a name', () => {
    expect(buildApiCallUrl('https://api.example.org', '/stations', [{ name: '', value: 'x' }])).toBe(
      'https://api.example.org/stations',
    )
  })

  it('returns the path with query parameters without a host', () => {
    expect(buildApiCallUrl('', '/stations', queryParameters)).toBe('/stations?status=active')
  })
})
