import { CONNECTOR_TYPES } from '@/const/connectors'

import {
  buildApiCallUrl,
  findSecretsToReenter,
  formatPathWithQuery,
  getConnectorDefaults,
  getConnectorFormData,
  toFormConnectorPath,
} from './connectors'

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

describe('findSecretsToReenter', () => {
  const BASE_URL = 'https://api.example.org/v1'
  const TOKEN_URL = 'https://auth.example.org/oauth2/token'
  const BASIC = { type: 'BASIC' as const, username: 'reader', password: '********' }
  const OAUTH2 = {
    type: 'OAUTH2' as const,
    tokenUrl: TOKEN_URL,
    clientId: 'client-1',
    clientSecret: '********',
    scope: '',
  }
  const CLIENT_CERTIFICATE = { type: 'CLIENT_CERTIFICATE' as const, certificate: '********', privateKey: '********' }
  const LOADED = { baseUrl: BASE_URL, authentication: BASIC }

  it.each([
    ['the same host with another path', 'https://api.example.org/v2/'],
    ['credentials in the URL', 'https://reader@api.example.org/v1'],
    ['a leading space', ' https://api.example.org/v1'],
    ['no API host', ''],
  ])('asks for no secret for %s', (_name, baseUrl) => {
    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, { baseUrl, authentication: BASIC }, LOADED)).toEqual([])
  })

  it.each([
    ['another host', 'https://other.example.org/v1'],
    ['another port', 'https://api.example.org:8443/v1'],
    ['another scheme', 'http://api.example.org/v1'],
    ['the default port', 'https://api.example.org:443/v1'],
    ['another case of the host', 'https://API.example.org/v1'],
  ])('asks for the secret again for %s', (_name, baseUrl) => {
    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, { baseUrl, authentication: BASIC }, LOADED)).toEqual([
      'authentication.password',
    ])
  })

  it('asks for all secrets of the client certificate', () => {
    const loaded = { baseUrl: BASE_URL, authentication: CLIENT_CERTIFICATE }

    expect(
      findSecretsToReenter(
        CONNECTOR_TYPES.HTTP,
        { baseUrl: 'https://other.example.org', authentication: CLIENT_CERTIFICATE },
        loaded,
      ),
    ).toEqual(['authentication.certificate', 'authentication.privateKey'])
  })

  it('asks for the client secret when the token URL changes', () => {
    const loaded = { baseUrl: BASE_URL, authentication: OAUTH2 }
    const current = { baseUrl: BASE_URL, authentication: { ...OAUTH2, tokenUrl: 'https://login.example.org/token' } }

    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, current, loaded)).toEqual(['authentication.clientSecret'])
  })

  it('asks for no secret when only the path of the token URL changes', () => {
    const loaded = { baseUrl: BASE_URL, authentication: OAUTH2 }
    const current = { baseUrl: BASE_URL, authentication: { ...OAUTH2, tokenUrl: 'https://auth.example.org/token' } }

    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, current, loaded)).toEqual([])
  })

  it('asks for no secret that the user entered again', () => {
    const current = { baseUrl: 'https://other.example.org', authentication: { ...BASIC, password: 'new-secret' } }

    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, current, LOADED)).toEqual([])
  })

  it('asks for no secret without a loaded connector of the same type', () => {
    const current = { baseUrl: 'https://other.example.org', authentication: BASIC }

    expect(findSecretsToReenter(CONNECTOR_TYPES.HTTP, current, undefined)).toEqual([])
  })

  describe('MQTT', () => {
    const LOADED_MQTT = {
      urls: 'mqtt://broker-1.example.org:1883, mqtt://broker-2.example.org:1883',
      password: '********',
    }

    it.each([
      ['the same brokers in another order', 'mqtt://broker-2.example.org:1883, mqtt://broker-1.example.org:1883'],
      ['a removed broker', 'mqtt://broker-1.example.org:1883'],
      ['credentials in the URL', 'mqtt://reader@broker-1.example.org:1883'],
    ])('asks for no password for %s', (_name, urls) => {
      expect(findSecretsToReenter(CONNECTOR_TYPES.MQTT, { ...LOADED_MQTT, urls }, LOADED_MQTT)).toEqual([])
    })

    it.each([
      ['another host', 'mqtt://broker-3.example.org:1883'],
      ['another port', 'mqtt://broker-1.example.org:8883'],
      ['another scheme', 'mqtts://broker-1.example.org:1883'],
      ['a jdbc prefix', 'jdbc:mqtt://broker-1.example.org:1883'],
    ])('asks for the password again for %s', (_name, urls) => {
      expect(findSecretsToReenter(CONNECTOR_TYPES.MQTT, { ...LOADED_MQTT, urls }, LOADED_MQTT)).toEqual(['password'])
    })

    it('asks for no password that the user entered again', () => {
      const current = { urls: 'mqtt://broker-3.example.org:1883', password: 'new-secret' }

      expect(findSecretsToReenter(CONNECTOR_TYPES.MQTT, current, LOADED_MQTT)).toEqual([])
    })
  })

  describe('SQL', () => {
    const LOADED_SQL = { dsn: 'postgres://db.example.org:5432/stations?sslmode=require', password: '********' }

    it.each([
      ['another database', 'postgres://db.example.org:5432/other?sslmode=require'],
      ['a jdbc prefix', 'jdbc:postgres://db.example.org:5432/stations?sslmode=require'],
      ['credentials in the DSN', 'postgres://reader@db.example.org:5432/stations?sslmode=require'],
      ['another parameter', 'postgres://db.example.org:5432/stations?sslmode=require&connect_timeout=5'],
    ])('asks for no password for %s', (_name, dsn) => {
      expect(findSecretsToReenter(CONNECTOR_TYPES.SQL, { ...LOADED_SQL, dsn }, LOADED_SQL)).toEqual([])
    })

    it.each([
      ['another host', 'postgres://other.example.org:5432/stations?sslmode=require'],
      ['another port', 'postgres://db.example.org:6432/stations?sslmode=require'],
      ['another sslmode', 'postgres://db.example.org:5432/stations?sslmode=disable'],
      ['no sslmode', 'postgres://db.example.org:5432/stations'],
      ['an sslmode only in the fragment', 'postgres://db.example.org:5432/stations#?sslmode=require'],
    ])('asks for the password again for %s', (_name, dsn) => {
      expect(findSecretsToReenter(CONNECTOR_TYPES.SQL, { ...LOADED_SQL, dsn }, LOADED_SQL)).toEqual(['password'])
    })
  })
})

describe('toFormConnectorPath', () => {
  const CONFIGURATION = {
    baseUrl: 'https://api.example.org/v1',
    tls: false,
    topics: 'sensors',
    apiCalls: [
      {
        name: 'Get all stations',
        path: '',
        headers: [
          { name: '', value: '' },
          { name: 'Host', value: 'x' },
        ],
        queryParameters: [{ name: 'status', value: 'active' }],
      },
    ],
  }

  it.each([
    ['a field', 'baseUrl', 'baseUrl'],
    ['an empty field', 'apiCalls.0.path', 'apiCalls.0.path'],
    ['a header after an empty row', 'apiCalls.0.headers.0.name', 'apiCalls.0.headers.1.name'],
    ['a query parameter', 'apiCalls.0.queryParameters.0.value', 'apiCalls.0.queryParameters.0.value'],
  ])('returns the form path of %s', (_name, path, formPath) => {
    expect(toFormConnectorPath(path, CONFIGURATION)).toBe(formPath)
  })

  it.each([
    ['an API call', 'apiCalls.0'],
    ['a part of a check box', 'tls.enabled'],
    ['a part of a text', 'topics.0'],
    ['a missing field', 'authentication.name'],
    ['a header that was not sent', 'apiCalls.0.headers.1.name'],
  ])('returns no form path for %s', (_name, path) => {
    expect(toFormConnectorPath(path, CONFIGURATION)).toBeUndefined()
  })

  it('skips a row whose name has only spaces', () => {
    const configuration = {
      apiCalls: [
        {
          queryParameters: [
            { name: '  ', value: '' },
            { name: 'status', value: 'active' },
          ],
        },
      ],
    }

    expect(toFormConnectorPath('apiCalls.0.queryParameters.0.name', configuration)).toBe(
      'apiCalls.0.queryParameters.1.name',
    )
  })
})
