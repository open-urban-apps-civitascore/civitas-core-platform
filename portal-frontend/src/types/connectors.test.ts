import { CONNECTOR_TYPES } from '@/const/connectors'

import {
  ConnectorApiToFormSchema,
  ConnectorFormToApiSchema,
  HttpLooseSchema,
  HttpStrictSchema,
  MqttLooseSchema,
  MqttStrictSchema,
} from './connectors'

const loose = (urls: string, tls: boolean) => MqttLooseSchema.safeParse({ urls, tls })

const strict = (urls: string, tls: boolean) =>
  MqttStrictSchema.safeParse({ urls, tls, topics: 'sensors/+/temperature', qos: 1, protocol_version: '3' })

describe.each([
  ['loose', loose],
  ['strict', strict],
])('MQTT %s schema broker URL validation', (_name, parse) => {
  it.each(['tcp://broker.example:1883', 'ws://broker.example:8080/mqtt', 'mqtt://broker.example:1883'])(
    'accepts plaintext URL %s when TLS is disabled',
    url => {
      expect(parse(url, false).success).toBe(true)
    },
  )

  it.each(['ssl://broker.example:8883', 'mqtts://broker.example:8883', 'wss://broker.example/mqtt'])(
    'accepts secure URL %s when TLS is enabled',
    url => {
      expect(parse(url, true).success).toBe(true)
    },
  )

  it.each([
    ['tcp://broker.example:1883', true],
    ['ws://broker.example/mqtt', true],
    ['mqtt://broker.example:1883', true],
    ['ssl://broker.example:8883', false],
    ['mqtts://broker.example:8883', false],
    ['wss://broker.example/mqtt', false],
  ])('rejects URL %s with tls=%s', (url, tls) => {
    const result = parse(url as string, tls as boolean)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['urls'], message: 'datasources.errors.brokerTlsMismatch' }),
      )
    }
  })

  it('rejects unknown schemes', () => {
    const result = parse('https://broker.example/mqtt', true)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['urls'], message: 'datasources.errors.unsupportedBrokerScheme' }),
      )
    }
  })

  it('checks every URL in a broker list', () => {
    expect(parse('ssl://one.example:8883, MQTTS://two.example:8883, wss://three.example/mqtt', true).success).toBe(true)
    expect(parse('tcp://one.example:1883, ssl://two.example:8883', false).success).toBe(false)
  })
})

describe('MQTT connector transformations', () => {
  it.each(['3', '5'] as const)('accepts MQTT protocol version %s', protocol_version => {
    expect(
      MqttStrictSchema.safeParse({
        urls: 'tcp://broker.example:1883',
        topics: 'sensors/#',
        qos: 1,
        protocol_version,
      }).success,
    ).toBe(true)
  })

  it('rejects unsupported MQTT protocol versions', () => {
    expect(
      MqttStrictSchema.safeParse({
        urls: 'tcp://broker.example:1883',
        topics: 'sensors/#',
        qos: 1,
        protocol_version: '4',
      }).success,
    ).toBe(false)
  })

  it('maps the form TLS switch to tls.enabled', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.MQTT,
      configuration: { urls: 'mqtts://broker.example:8883', tls: true },
    })

    expect(result).toMatchObject({ connectorType: CONNECTOR_TYPES.MQTT, tls: { enabled: true } })
  })

  it('maps tls.enabled from the API to the form switch', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.MQTT,
      configuration: { urls: ['ssl://broker.example:8883'], tls: { enabled: true }, protocol_version: '3' },
    })

    expect(result).toMatchObject({ configuration: { tls: true } })
  })

  it('rejects an API response without a protocol version', () => {
    // The backend always resolves a concrete protocol_version (defaulting legacy datasources to
    // MQTT v3 itself), so the frontend no longer needs — or accepts — a missing value here.
    expect(
      ConnectorApiToFormSchema.safeParse({
        connectorType: CONNECTOR_TYPES.MQTT,
        configuration: { urls: ['tcp://broker.example:1883'], tls: { enabled: false } },
      }).success,
    ).toBe(false)
  })
})

describe('HTTP connector schemas', () => {
  const API_CALL = { name: 'Get all stations', path: '/stations' }
  const BASIC = { type: 'BASIC' as const, username: 'reader', password: 'secret' }
  const NONE = { type: 'NONE' as const, username: '', password: '' }

  const looseIssues = (configuration: Record<string, unknown>) => {
    const result = HttpLooseSchema.safeParse(configuration)
    return result.success ? [] : result.error.issues
  }

  it.each([
    'https://api.example.org/v1',
    'http://api.example.org',
    'https://api.example.org:8443/v1/',
    'https://api.example.org/users/@me',
    'https://api.example.org/v1/%2F',
    'https://api.example.org./v1',
    'https://1api.example.org',
    'HTTPS://API.Example.org',
    'https://192.168.0.1:8080/v1',
    'https://[::1]:8080/v1',
  ])('accepts the API host %s', baseUrl => {
    expect(looseIssues({ baseUrl })).toEqual([])
  })

  it.each([
    'ftp://api.example.org',
    'https://',
    'api.example.org',
    'https://api.example.org/v1?key=1',
    'https://api.example.org/v1#part',
    'https://api.example.org/my path',
    'https://api.example.org/%zz',
    'https:\\\\api.example.org',
    'https:api.example.org',
    'https:/api.example.org',
    'https://api_example.org/v1',
    'https://-api.example.org',
    'https://a..b.org',
    'https://api.example.123',
    'https://bücher.de',
    'https://999.1.1.1',
    'https://api.example.org/{id}',
    'https://api.example.org/a|b',
    'https://api.example.org/a"b',
    'https://api.example.org/a<b>',
    'https://api.example.org/a^b',
    'https://api.example.org/a`b',
    'https://api.example.org/a[1]',
    'https://api.example.org /v1',
    'https://api.example.org/v1/weather\u00A0data',
    'https://api.example.org/v1/weather\u0085data',
  ])('rejects the invalid API host %s', baseUrl => {
    expect(looseIssues({ baseUrl })).toContainEqual(
      expect.objectContaining({ path: ['baseUrl'], message: 'datasources.errors.invalidBaseUrl' }),
    )
  })

  it.each(['https://reader:pw@api.example.org/v1', 'https://reader@api.example.org/v1'])(
    'rejects credentials in the API host %s',
    baseUrl => {
      expect(looseIssues({ baseUrl })).toContainEqual(
        expect.objectContaining({ path: ['baseUrl'], message: 'datasources.errors.baseUrlCredentials' }),
      )
    },
  )

  it('accepts an empty API host in a draft', () => {
    expect(looseIssues({ baseUrl: '', authentication: NONE })).toEqual([])
  })

  it('rejects a username with a colon', () => {
    expect(looseIssues({ authentication: { ...BASIC, username: 'read:er' } })).toContainEqual(
      expect.objectContaining({ path: ['authentication', 'username'], message: 'datasources.errors.usernameColon' }),
    )
  })

  it('ignores a hidden username with a colon for None', () => {
    expect(looseIssues({ authentication: { ...NONE, username: 'read:er' } })).toEqual([])
  })

  it('accepts a complete configuration for release', () => {
    const result = HttpStrictSchema.safeParse({
      baseUrl: 'https://api.example.org/v1',
      authentication: BASIC,
      apiCalls: [API_CALL],
    })
    expect(result.success).toBe(true)
  })

  it.each([
    ['baseUrl', { baseUrl: '', authentication: NONE, apiCalls: [API_CALL] }, ['baseUrl']],
    [
      'username',
      { baseUrl: 'https://a.example', authentication: { ...BASIC, username: '' }, apiCalls: [API_CALL] },
      ['authentication', 'username'],
    ],
    [
      'password',
      { baseUrl: 'https://a.example', authentication: { ...BASIC, password: '' }, apiCalls: [API_CALL] },
      ['authentication', 'password'],
    ],
    ['an API call', { baseUrl: 'https://a.example', authentication: NONE, apiCalls: [] }, ['apiCalls']],
  ])('requires %s for release', (_name, configuration, path) => {
    const result = HttpStrictSchema.safeParse(configuration)
    expect(result.success).toBe(false)
    expect(result.error?.issues).toContainEqual(expect.objectContaining({ path }))
  })

  it('maps a missing authentication from the API to None with empty credentials', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { baseUrl: 'https://api.example.org/v1', authentication: null, apiCalls: null },
    })

    expect(result.configuration).toEqual({
      baseUrl: 'https://api.example.org/v1',
      authentication: NONE,
      apiCalls: undefined,
    })
  })

  it('sends only the type for None', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { baseUrl: 'https://api.example.org/v1', authentication: { ...NONE, username: 'old' } },
    })

    expect(result).toEqual({
      connectorType: CONNECTOR_TYPES.HTTP,
      baseUrl: 'https://api.example.org/v1',
      authentication: { type: 'NONE' },
    })
  })

  it('sends the API host without trailing slashes', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { baseUrl: 'https://api.example.org/v1//', authentication: NONE },
    })

    expect(result).toMatchObject({ baseUrl: 'https://api.example.org/v1' })
  })

  it('sends the Basic credentials as entered', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { authentication: BASIC },
    })

    expect(result).toMatchObject({ authentication: BASIC })
  })
})
