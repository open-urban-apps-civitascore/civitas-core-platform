import {
  CONNECTOR_TYPES,
  EMPTY_CURSOR_PAGINATION,
  EMPTY_LIMIT_OFFSET_PAGINATION,
  EMPTY_PAGINATION,
} from '@/const/connectors'
import { DataSourceDraftSchema } from '@/generated/core'
import {
  CURSOR_PAGINATION_API,
  CURSOR_PAGINATION_FORM,
  LIMIT_OFFSET_PAGINATION_API,
  LIMIT_OFFSET_PAGINATION_FORM,
  PAGINATION_API,
  PAGINATION_FORM,
} from '@/test-support/paginationFixtures'
import { emptyHttpAuthentication } from '@/utils/connectorMappers'

import {
  ConnectorApiToFormSchema,
  ConnectorFormToApiSchema,
  HttpApiToFormSchema,
  HttpLooseSchema,
  HttpStrictSchema,
  MqttLooseSchema,
  MqttStrictSchema,
} from './connectors'
import {
  HttpApiCallForm,
  HttpCursorPaginationForm,
  HttpLimitOffsetPaginationForm,
  HttpPaginationForm,
  HttpParameterLocation,
} from './httpApiCalls'

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

  it.each(['tcp:broker', 'tcp://:1883'])('rejects URL %s without host', url => {
    const result = parse(`tcp://broker.example:1883, ${url}`, false)
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ path: ['urls'], message: 'datasources.errors.invalidBrokerUrl' }),
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
  const API_CALL = {
    name: 'Get all stations',
    path: '/stations',
    queryParameters: [],
    headers: [],
    pagination: EMPTY_PAGINATION,
  }
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

  it('maps a missing authentication from the API to None', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { baseUrl: 'https://api.example.org/v1', authentication: null, apiCalls: null },
    })

    expect(result.configuration).toEqual({
      baseUrl: 'https://api.example.org/v1',
      authentication: { type: 'NONE' },
      apiCalls: [],
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

  describe('authentication', () => {
    const BASE_URL = 'https://api.example.org/v1'
    const CERTIFICATE = '-----BEGIN CERTIFICATE-----\nMIIBtest1\n-----END CERTIFICATE-----'
    const SECOND_CERTIFICATE = '-----BEGIN CERTIFICATE-----\nMIIBtest2\n-----END CERTIFICATE-----'
    const PRIVATE_KEY = '-----BEGIN PRIVATE KEY-----\nMIIEtest\n-----END PRIVATE KEY-----'
    const RSA_PRIVATE_KEY = '-----BEGIN RSA PRIVATE KEY-----\nMIIEtest\n-----END RSA PRIVATE KEY-----'
    const ENCRYPTED_PRIVATE_KEY = '-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIIEtest\n-----END ENCRYPTED PRIVATE KEY-----'
    const ENCRYPTED_RSA_PRIVATE_KEY =
      '-----BEGIN RSA PRIVATE KEY-----\nProc-Type: 4,ENCRYPTED\nDEK-Info: AES-256-CBC,0011\n\nMIIEtest\n-----END RSA PRIVATE KEY-----'
    const AUTHENTICATIONS = {
      BASIC,
      BEARER_TOKEN: { type: 'BEARER_TOKEN' as const, token: 'token-1' },
      API_TOKEN: {
        type: 'API_TOKEN' as const,
        name: 'X-API-Key',
        value: 'key-1',
        in: 'HEADER' as HttpParameterLocation,
      },
      OAUTH2: {
        type: 'OAUTH2' as const,
        tokenUrl: 'https://auth.example.org/oauth2/token',
        clientId: 'client-1',
        clientSecret: 'secret-1',
        scope: 'read write',
      },
      CLIENT_CERTIFICATE: { type: 'CLIENT_CERTIFICATE' as const, certificate: CERTIFICATE, privateKey: PRIVATE_KEY },
    }
    const API_TOKEN = AUTHENTICATIONS.API_TOKEN
    const OAUTH2 = AUTHENTICATIONS.OAUTH2
    const CLIENT_CERTIFICATE = AUTHENTICATIONS.CLIENT_CERTIFICATE

    const loadAuthentication = (authentication: Record<string, unknown>) =>
      HttpApiToFormSchema.parse({ baseUrl: BASE_URL, authentication, apiCalls: null }).authentication

    const sendAuthentication = (authentication: Record<string, unknown>) =>
      ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { baseUrl: BASE_URL, authentication, apiCalls: [API_CALL] },
      }) as { connectorType: string; authentication: Record<string, unknown> }

    const strictIssues = (authentication: Record<string, unknown>, configuration: Record<string, unknown> = {}) => {
      const result = HttpStrictSchema.safeParse({
        baseUrl: BASE_URL,
        authentication,
        apiCalls: [API_CALL],
        ...configuration,
      })
      return result.success ? [] : result.error.issues
    }

    const issue = (path: (string | number)[], message: string) => expect.objectContaining({ path, message })

    it.each(Object.values(AUTHENTICATIONS))('loads the authentication $type', authentication => {
      expect(loadAuthentication(authentication)).toEqual(authentication)
    })

    it('loads None', () => {
      expect(loadAuthentication({ type: 'NONE' })).toEqual({ type: 'NONE' })
    })

    it.each([
      ['BASIC', { username: 'reader', password: '********' }],
      ['BEARER_TOKEN', { token: '********' }],
      ['API_TOKEN', { name: 'X-API-Key', value: '********', in: 'QUERY' }],
      ['OAUTH2', { tokenUrl: OAUTH2.tokenUrl, clientId: 'client-1', clientSecret: '********', scope: '' }],
      ['CLIENT_CERTIFICATE', { certificate: '********', privateKey: '********' }],
    ])('keeps the masked secrets of %s', (type, fields) => {
      expect(loadAuthentication({ type, ...fields })).toEqual({ type, ...fields })
    })

    it.each([
      ['BASIC', { username: null, password: null }, { username: '', password: '' }],
      ['BEARER_TOKEN', { token: null }, { token: '' }],
      ['API_TOKEN', { name: null, value: null, in: null }, { name: '', value: '', in: 'HEADER' }],
      [
        'OAUTH2',
        { tokenUrl: null, clientId: null, clientSecret: null, scope: null },
        { tokenUrl: '', clientId: '', clientSecret: '', scope: '' },
      ],
      ['CLIENT_CERTIFICATE', { certificate: null, privateKey: null }, { certificate: '', privateKey: '' }],
    ])('loads the empty values of %s as empty fields', (type, apiFields, formFields) => {
      expect(loadAuthentication({ type, ...apiFields })).toEqual({ type, ...formFields })
    })

    it.each([
      ['NONE', { type: 'NONE' }],
      ['BASIC', { type: 'BASIC', username: '', password: '' }],
      ['BEARER_TOKEN', { type: 'BEARER_TOKEN', token: '' }],
      ['API_TOKEN', { type: 'API_TOKEN', name: '', value: '', in: 'HEADER' }],
      ['OAUTH2', { type: 'OAUTH2', tokenUrl: '', clientId: '', clientSecret: '', scope: '' }],
      ['CLIENT_CERTIFICATE', { type: 'CLIENT_CERTIFICATE', certificate: '', privateKey: '' }],
    ] as const)('creates the empty fields of %s', (type, expected) => {
      expect(emptyHttpAuthentication(type)).toEqual(expected)
    })

    it.each(Object.values(AUTHENTICATIONS))('sends only the fields of $type', authentication => {
      const result = sendAuthentication({ username: 'other', token: 'other', scope: 'other', ...authentication })

      expect(result).toMatchObject({ authentication })
      expect(Object.keys(result.authentication)).toEqual(Object.keys(authentication))
    })

    it.each([{ type: 'NONE' }, ...Object.values(AUTHENTICATIONS)])(
      'sends a payload for $type that the CORE schema accepts',
      authentication => {
        const { connectorType, ...connectorFields } = sendAuthentication(authentication)
        const coreDoc = { connectionType: connectorType.toLowerCase(), ...connectorFields }

        expect(DataSourceDraftSchema.safeParse(coreDoc).success).toBe(true)
      },
    )

    it('sends the certificate and the private key without surrounding whitespace', () => {
      const result = sendAuthentication({
        ...CLIENT_CERTIFICATE,
        certificate: `\n${CERTIFICATE}\n`,
        privateKey: `\n${PRIVATE_KEY}\n`,
      })

      expect(result.authentication).toEqual(CLIENT_CERTIFICATE)
    })

    it('sends an empty OAuth2 scope as null, so the backend removes a stored scope', () => {
      const result = sendAuthentication({ ...OAUTH2, scope: ' ' })

      expect(result.authentication).toEqual({ ...OAUTH2, scope: null })
    })

    it('sends None for a missing authentication', () => {
      const result = ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { baseUrl: BASE_URL },
      })

      expect(result).toMatchObject({ authentication: { type: 'NONE' } })
    })

    it.each(Object.values(AUTHENTICATIONS))('accepts a complete $type for release', authentication => {
      expect(strictIssues(authentication)).toEqual([])
    })

    it.each([
      ['BEARER_TOKEN', 'token'],
      ['API_TOKEN', 'name'],
      ['API_TOKEN', 'value'],
      ['OAUTH2', 'tokenUrl'],
      ['OAUTH2', 'clientId'],
      ['OAUTH2', 'clientSecret'],
      ['CLIENT_CERTIFICATE', 'certificate'],
      ['CLIENT_CERTIFICATE', 'privateKey'],
    ] as const)('requires %s %s for release', (type, field) => {
      expect(strictIssues({ ...AUTHENTICATIONS[type], [field]: '' })).toContainEqual(
        issue(['authentication', field], 'common.errors.required'),
      )
    })

    it.each([
      ['BASIC', 'password'],
      ['BEARER_TOKEN', 'token'],
      ['API_TOKEN', 'value'],
      ['OAUTH2', 'clientSecret'],
    ] as const)('requires a %s %s with more than spaces for release', (type, field) => {
      expect(strictIssues({ ...AUTHENTICATIONS[type], [field]: '   ' })).toContainEqual(
        issue(['authentication', field], 'common.errors.required'),
      )
    })

    it('sends a secret with spaces as entered', () => {
      const result = sendAuthentication({ ...AUTHENTICATIONS.BEARER_TOKEN, token: ' token-1 ' })

      expect(result.authentication).toEqual({ type: 'BEARER_TOKEN', token: ' token-1 ' })
    })

    it('does not require the OAuth2 scope for release', () => {
      expect(strictIssues({ ...OAUTH2, scope: '' })).toEqual([])
    })

    it.each(Object.values(AUTHENTICATIONS))('accepts an empty $type in a draft', ({ type }) => {
      expect(looseIssues({ baseUrl: BASE_URL, authentication: emptyHttpAuthentication(type) })).toEqual([])
    })

    it.each(Object.values(AUTHENTICATIONS))('rejects an http API host for $type', authentication => {
      expect(looseIssues({ baseUrl: 'HTTP://api.example.org', authentication })).toContainEqual(
        issue(['baseUrl'], 'datasources.errors.baseUrlHttpsRequired'),
      )
    })

    it('accepts an http API host for None', () => {
      expect(looseIssues({ baseUrl: 'http://api.example.org', authentication: { type: 'NONE' } })).toEqual([])
    })

    it.each([
      ['http://auth.example.org/token', 'datasources.errors.tokenUrlHttps'],
      ['https://auth.example.org/token?scope=read', 'datasources.errors.tokenUrlQueryOrFragment'],
      ['https://auth.example.org/token#part', 'datasources.errors.tokenUrlQueryOrFragment'],
      ['https://client:secret@auth.example.org/token', 'datasources.errors.tokenUrlCredentials'],
      ['auth.example.org/token', 'datasources.errors.invalidTokenUrl'],
      ['ftp://auth.example.org/token', 'datasources.errors.invalidTokenUrl'],
    ])('rejects the token URL %s', (tokenUrl, message) => {
      expect(looseIssues({ authentication: { ...OAUTH2, tokenUrl } })).toContainEqual(
        issue(['authentication', 'tokenUrl'], message),
      )
    })

    it('accepts an https token URL in any case', () => {
      expect(looseIssues({ authentication: { ...OAUTH2, tokenUrl: 'HTTPS://auth.example.org/token' } })).toEqual([])
    })

    it.each([
      ['an invalid header name', 'Bad Name', 'datasources.errors.invalidHeaderName'],
      ['a header the HTTP client sets', 'Host', 'datasources.errors.clientHeader'],
    ])('rejects %s as API token header', (_name, name, message) => {
      expect(looseIssues({ authentication: { ...API_TOKEN, name } })).toContainEqual(
        issue(['authentication', 'name'], message),
      )
    })

    it.each(['Authorization', 'Proxy-Authorization', 'Cookie'])('accepts %s as API token header', name => {
      expect(looseIssues({ authentication: { ...API_TOKEN, name } })).toEqual([])
    })

    it('accepts as API token query parameter what is not allowed in a header', () => {
      expect(looseIssues({ authentication: { ...API_TOKEN, name: 'Bad Name', in: 'QUERY' } })).toEqual([])
    })

    describe('API token name collision', () => {
      const collisionIssues = (token: Partial<typeof API_TOKEN>, apiCall: Partial<HttpApiCallForm>) =>
        looseIssues({
          baseUrl: BASE_URL,
          authentication: { ...API_TOKEN, ...token },
          apiCalls: [{ ...API_CALL, ...apiCall }],
        })
      const collision = issue(['authentication', 'name'], 'datasources.errors.apiTokenNameCollision')
      const parameterIssue = (path: (string | number)[]) =>
        issue(['apiCalls', 0, ...path], 'datasources.errors.parameterApiTokenDuplicate')

      it.each<[string, Partial<typeof API_TOKEN>, Partial<HttpApiCallForm>, (string | number)[]]>([
        [
          'the header after an empty row',
          { name: 'X-API-Key' },
          {
            headers: [
              { name: '', value: '' },
              { name: 'x-api-key', value: 'x' },
            ],
          },
          ['headers', 1, 'name'],
        ],
        [
          'the query parameter',
          { name: 'key', in: 'QUERY' },
          { queryParameters: [{ name: 'key', value: 'x' }] },
          ['queryParameters', 0, 'name'],
        ],
        [
          'the page parameter',
          { name: 'page', in: 'QUERY' },
          { pagination: PAGINATION_FORM },
          ['pagination', 'pageParameter'],
        ],
      ])('shows the collision also at %s', (_name, token, apiCall, path) => {
        expect(collisionIssues(token, apiCall)).toContainEqual(parameterIssue(path))
      })

      it.each<[string, Partial<typeof API_TOKEN>, Partial<HttpApiCallForm>]>([
        ['a header in another case', { name: 'X-API-Key' }, { headers: [{ name: 'x-api-key', value: 'x' }] }],
        ['a query parameter', { name: 'key', in: 'QUERY' }, { queryParameters: [{ name: 'key', value: 'x' }] }],
        ['the page parameter', { name: 'page', in: 'QUERY' }, { pagination: PAGINATION_FORM }],
        [
          'the offset parameter',
          { name: 'offset', in: 'QUERY' },
          { pagination: { ...EMPTY_LIMIT_OFFSET_PAGINATION, isEnabled: true, offsetParameter: 'offset' } },
        ],
        [
          'the limit parameter',
          { name: 'limit', in: 'QUERY' },
          {
            pagination: {
              ...EMPTY_LIMIT_OFFSET_PAGINATION,
              isEnabled: true,
              offsetParameter: 'o',
              limitParameter: 'limit',
              limit: '5',
            },
          },
        ],
        [
          'a cursor query parameter',
          { name: 'cursor', in: 'QUERY' },
          { pagination: { ...EMPTY_CURSOR_PAGINATION, isEnabled: true, cursorParameter: 'cursor' } },
        ],
        [
          'a cursor header',
          { name: 'X-Cursor' },
          {
            pagination: {
              ...EMPTY_CURSOR_PAGINATION,
              isEnabled: true,
              cursorParameter: 'x-cursor',
              cursorIn: 'HEADER',
            },
          },
        ],
      ])('rejects a name equal to %s', (_name, token, apiCall) => {
        expect(collisionIssues(token, apiCall)).toContainEqual(collision)
      })

      it.each<[string, Partial<typeof API_TOKEN>, Partial<HttpApiCallForm>]>([
        [
          'a query parameter in another case',
          { name: 'key', in: 'QUERY' },
          { queryParameters: [{ name: 'Key', value: 'x' }] },
        ],
        ['a query parameter for a header', { name: 'key' }, { queryParameters: [{ name: 'key', value: 'x' }] }],
        ['a header for a query parameter', { name: 'key', in: 'QUERY' }, { headers: [{ name: 'key', value: 'x' }] }],
        [
          'a disabled pagination',
          { name: 'page', in: 'QUERY' },
          { pagination: { ...PAGINATION_FORM, isEnabled: false } },
        ],
      ])('accepts a name equal to %s', (_name, token, apiCall) => {
        expect(collisionIssues(token, apiCall)).not.toContainEqual(collision)
      })

      it('rejects a collision for release', () => {
        const apiCall = { ...API_CALL, headers: [{ name: 'X-API-Key', value: 'x' }] }
        expect(strictIssues(API_TOKEN, { apiCalls: [apiCall] })).toContainEqual(collision)
      })
    })

    it.each([
      ['a certificate chain', { certificate: `${CERTIFICATE}\n\n${SECOND_CERTIFICATE}\n` }],
      ['a PKCS#8 key', { privateKey: PRIVATE_KEY }],
      ['a PKCS#1 key', { privateKey: RSA_PRIVATE_KEY }],
      ['a key after an empty line', { privateKey: `\n${PRIVATE_KEY}` }],
      ['a key without footer', { privateKey: '-----BEGIN PRIVATE KEY-----\nMIIEtest' }],
      ['masked values', { certificate: '********', privateKey: '********' }],
    ])('accepts %s', (_name, fields) => {
      expect(looseIssues({ authentication: { ...CLIENT_CERTIFICATE, ...fields } })).toEqual([])
    })

    it.each([
      [
        'text before the certificate',
        { certificate: `subject=Test\n${CERTIFICATE}` },
        'certificate',
        'invalidCertificate',
      ],
      ['text after the certificate', { certificate: `${CERTIFICATE}\nend` }, 'certificate', 'invalidCertificate'],
      ['a private key as certificate', { certificate: PRIVATE_KEY }, 'certificate', 'invalidCertificate'],
      ['an encrypted key', { privateKey: ENCRYPTED_PRIVATE_KEY }, 'privateKey', 'encryptedPrivateKey'],
      ['an encrypted RSA key', { privateKey: ENCRYPTED_RSA_PRIVATE_KEY }, 'privateKey', 'encryptedPrivateKey'],
      ['a certificate as key', { privateKey: CERTIFICATE }, 'privateKey', 'invalidPrivateKey'],
      ['other text as key', { privateKey: 'secret' }, 'privateKey', 'invalidPrivateKey'],
      [
        'a key with header and body on one line',
        { privateKey: '-----BEGIN PRIVATE KEY-----MIIEtest\n-----END PRIVATE KEY-----' },
        'privateKey',
        'invalidPrivateKey',
      ],
      [
        'a key with a space after the header',
        { privateKey: '-----BEGIN PRIVATE KEY----- \nMIIEtest\n-----END PRIVATE KEY-----' },
        'privateKey',
        'invalidPrivateKey',
      ],
      [
        'a key with body and footer on one line',
        { privateKey: '-----BEGIN PRIVATE KEY-----\nMIIEtest-----END PRIVATE KEY-----' },
        'privateKey',
        'invalidPrivateKey',
      ],
      [
        'a key with the footer of another type',
        { privateKey: '-----BEGIN PRIVATE KEY-----\nMIIEtest\n-----END RSA PRIVATE KEY-----' },
        'privateKey',
        'invalidPrivateKey',
      ],
    ])('rejects %s', (_name, fields, field, message) => {
      expect(looseIssues({ authentication: { ...CLIENT_CERTIFICATE, ...fields } })).toContainEqual(
        issue(['authentication', field], `datasources.errors.${message}`),
      )
    })
  })
})

describe('HTTP API call schemas', () => {
  const API_CALL = {
    name: 'Get all stations',
    path: '/stations',
    queryParameters: [],
    headers: [],
    pagination: EMPTY_PAGINATION,
  }
  const HEADER = { name: 'Accept', value: 'application/json' }
  const issues = (schema: typeof HttpLooseSchema | typeof HttpStrictSchema, apiCalls: unknown[]) => {
    const result = schema.shape.apiCalls.safeParse(apiCalls)
    return result.success ? [] : result.error.issues
  }

  it.each(['/', '/v2/stations', '/stations/:id', '/a-b_c.d~e'])('accepts the path %s', path => {
    expect(issues(HttpLooseSchema, [{ ...API_CALL, path }])).toEqual([])
  })

  it.each(['stations', '/a b', '/a?x', '/a#b', '/ä'])('rejects the path %s', path => {
    expect(issues(HttpLooseSchema, [{ ...API_CALL, path }])).toContainEqual(
      expect.objectContaining({ path: [0, 'path'], message: 'datasources.errors.invalidPath' }),
    )
  })

  it.each([
    ['loose', HttpLooseSchema],
    ['strict', HttpStrictSchema],
  ] as const)('rejects a second API call in the %s schema', (_name, schema) => {
    expect(issues(schema, [API_CALL, { ...API_CALL, name: 'Other' }])).toContainEqual(
      expect.objectContaining({ path: [], message: 'datasources.errors.apiCallsMax' }),
    )
  })

  it.each([
    ['an invalid header name', 'Bad Name', 'datasources.errors.invalidHeaderName'],
    ['an Authorization header', 'Authorization', 'datasources.errors.credentialHeader'],
    ['a cookie header', 'cookie', 'datasources.errors.credentialHeader'],
    ['a header the HTTP client sets', 'Host', 'datasources.errors.clientHeader'],
  ])('rejects %s', (_name, name, message) => {
    expect(issues(HttpLooseSchema, [{ ...API_CALL, headers: [{ name, value: 'x' }] }])).toContainEqual(
      expect.objectContaining({ path: [0, 'headers', 0, 'name'], message }),
    )
  })

  it.each(['a\nb', 'a\rb'])('rejects the header value %j', value => {
    expect(issues(HttpLooseSchema, [{ ...API_CALL, headers: [{ name: 'Accept', value }] }])).toContainEqual(
      expect.objectContaining({ path: [0, 'headers', 0, 'value'], message: 'datasources.errors.headerValueLineBreak' }),
    )
  })

  it('accepts as a query parameter what is not allowed in a header', () => {
    const queryParameters = [
      { name: 'Authorization', value: 'x' },
      { name: 'Bad Name', value: 'x' },
      { name: 'note', value: 'a\nb' },
    ]
    expect(issues(HttpLooseSchema, [{ ...API_CALL, queryParameters }])).toEqual([])
  })

  it('accepts an incomplete API call in a draft', () => {
    const apiCall = { ...API_CALL, name: '', path: '', queryParameters: [{ name: '', value: '' }] }
    expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
  })

  it('accepts a complete API call for release', () => {
    expect(issues(HttpStrictSchema, [{ ...API_CALL, headers: [HEADER] }])).toEqual([])
  })

  it('accepts empty parameter rows for release', () => {
    const empty = { name: '', value: '' }
    expect(issues(HttpStrictSchema, [{ ...API_CALL, queryParameters: [empty], headers: [empty] }])).toEqual([])
  })

  it('accepts a parameter with a name and an empty value for release', () => {
    expect(issues(HttpStrictSchema, [{ ...API_CALL, queryParameters: [{ name: 'debug', value: '' }] }])).toEqual([])
  })

  it.each([
    ['the name', { ...API_CALL, name: '' }, [0, 'name']],
    ['the path', { ...API_CALL, path: '' }, [0, 'path']],
    [
      'a query parameter name',
      { ...API_CALL, queryParameters: [{ name: '', value: 'x' }] },
      [0, 'queryParameters', 0, 'name'],
    ],
    ['a header name', { ...API_CALL, headers: [{ name: '', value: 'x' }] }, [0, 'headers', 0, 'name']],
  ])('requires %s for release', (_name, apiCall, path) => {
    expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(
      expect.objectContaining({ path, message: 'common.errors.required' }),
    )
  })

  it('maps API calls from the API to the form', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: {
        apiCalls: [
          {
            name: null,
            method: 'GET',
            path: '/stations',
            queryParameters: [{ name: 'status', value: null }],
            headers: null,
            pagination: PAGINATION_API,
          },
        ],
      },
    })

    expect(result.configuration).toMatchObject({
      apiCalls: [
        {
          name: '',
          method: 'GET',
          path: '/stations',
          queryParameters: [{ name: 'status', value: '' }],
          headers: [],
          pagination: PAGINATION_FORM,
        },
      ],
    })
  })

  it('keeps an API call with an unknown method and leaves out the method', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { apiCalls: [{ name: 'Get all stations', method: 'UNKNOWN', path: '/stations' }] },
    })

    expect(result.configuration).toMatchObject({ apiCalls: [{ name: 'Get all stations', method: undefined }] })
  })

  it('maps empty pagination values from the API to the form', () => {
    const pagination = { type: 'PAGE_NUMBER', pageParameter: null, pageSizeParameter: null, pageSize: null }
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: {
        apiCalls: [{ path: '/stations', pagination: { ...pagination, startPage: null } }, { path: '/a' }],
      },
    })

    expect(result.configuration).toMatchObject({
      apiCalls: [{ pagination: { ...EMPTY_PAGINATION, isEnabled: true } }, { pagination: EMPTY_PAGINATION }],
    })
  })

  it('leaves out parameter rows without name and value when sending', () => {
    const empty = { name: '', value: '' }
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: {
        apiCalls: [{ ...API_CALL, queryParameters: [empty, { name: 'debug', value: '' }], headers: [empty] }],
      },
    })

    expect(result).toMatchObject({
      apiCalls: [{ queryParameters: [{ name: 'debug', value: '' }], headers: [] }],
    })
  })

  it('sends API calls without the method and with the pagination as numbers', () => {
    const apiCall = { ...API_CALL, method: 'GET', headers: [HEADER], pagination: PAGINATION_FORM }
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { apiCalls: [apiCall] },
    })

    expect(result).toEqual({
      connectorType: CONNECTOR_TYPES.HTTP,
      authentication: { type: 'NONE' },
      apiCalls: [{ ...API_CALL, headers: [HEADER], pagination: PAGINATION_API }],
    })
  })

  it('sends empty pagination values as null', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: {
        apiCalls: [{ ...API_CALL, pagination: { ...EMPTY_PAGINATION, isEnabled: true, startPage: '' } }],
      },
    })

    expect(result).toMatchObject({
      apiCalls: [
        {
          pagination: {
            type: 'PAGE_NUMBER',
            pageParameter: null,
            pageSizeParameter: null,
            pageSize: null,
            startPage: null,
            endPage: null,
          },
        },
      ],
    })
  })

  it('sends a disabled pagination with values as null', () => {
    const result = ConnectorFormToApiSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { apiCalls: [{ ...API_CALL, pagination: { ...PAGINATION_FORM, isEnabled: false } }] },
    })

    expect(result).toMatchObject({ apiCalls: [{ pagination: null }] })
  })

  describe('pagination', () => {
    const withPagination = (pagination: Partial<HttpPaginationForm>) => ({
      ...API_CALL,
      pagination: { ...EMPTY_PAGINATION, isEnabled: true, ...pagination },
    })

    it('accepts empty fields in a draft', () => {
      expect(issues(HttpLooseSchema, [withPagination({ startPage: '' })])).toEqual([])
    })

    it('accepts a complete pagination for release', () => {
      expect(issues(HttpStrictSchema, [{ ...API_CALL, pagination: PAGINATION_FORM }])).toEqual([])
    })

    it.each(['page', 'page[number]', 'page-no 1'])('accepts the page parameter %s', pageParameter => {
      expect(issues(HttpLooseSchema, [withPagination({ pageParameter })])).toEqual([])
    })

    it.each([
      ['a decimal page size', { pageSize: '1.5' }, 'pageSize', 'pageNumberInteger'],
      ['a negative start page', { startPage: '-1' }, 'startPage', 'pageNumberInteger'],
      ['a text end page', { endPage: 'ten' }, 'endPage', 'pageNumberInteger'],
      ['the page size 0', { pageSize: '0' }, 'pageSize', 'pageSizeMin'],
      ['a page size above the maximum', { pageSize: '2147483648' }, 'pageSize', 'pageNumberMax'],
      ['a start page above the maximum', { startPage: '3000000000' }, 'startPage', 'pageNumberMax'],
      ['an end page above the maximum', { endPage: '99999999999' }, 'endPage', 'pageNumberMax'],
      ['an end page before the start page', { startPage: '5', endPage: '4' }, 'endPage', 'endPageBeforeStartPage'],
    ])('rejects %s', (_name, pagination, field, error) => {
      expect(issues(HttpLooseSchema, [withPagination(pagination)])).toContainEqual(
        expect.objectContaining({ path: [0, 'pagination', field], message: `datasources.errors.${error}` }),
      )
    })

    it('does not compare the end page with a start page above the maximum', () => {
      const result = issues(HttpLooseSchema, [withPagination({ startPage: '3000000000', endPage: '5' })])
      expect(result).toEqual([
        expect.objectContaining({ path: [0, 'pagination', 'startPage'], message: 'datasources.errors.pageNumberMax' }),
      ])
    })

    it('accepts the maximum page number', () => {
      const pagination = { pageSize: '2147483647', startPage: '2147483647', endPage: '2147483647' }
      expect(issues(HttpLooseSchema, [withPagination(pagination)])).toEqual([])
    })

    it('accepts an end page equal to the start page', () => {
      expect(issues(HttpLooseSchema, [withPagination({ startPage: '3', endPage: '3' })])).toEqual([])
    })

    it('compares the end page with start page 0 when the start page is empty', () => {
      expect(issues(HttpLooseSchema, [withPagination({ startPage: '', endPage: '0' })])).toEqual([])
    })

    it.each(['pageParameter', 'pageSizeParameter', 'pageSize'])('requires %s for release', field => {
      expect(
        issues(HttpStrictSchema, [{ ...API_CALL, pagination: { ...PAGINATION_FORM, [field]: '' } }]),
      ).toContainEqual(expect.objectContaining({ path: [0, 'pagination', field], message: 'common.errors.required' }))
    })

    it('rejects a page size parameter equal to the page parameter once at each field', () => {
      const result = issues(HttpLooseSchema, [withPagination({ pageParameter: 'page', pageSizeParameter: 'page' })])
      const duplicates = result.filter(issue => issue.message === 'datasources.errors.paginationParameterDuplicate')
      const paths = duplicates.map(issue => issue.path)
      expect(paths).toHaveLength(2)
      expect(paths).toEqual(
        expect.arrayContaining([
          [0, 'pagination', 'pageParameter'],
          [0, 'pagination', 'pageSizeParameter'],
        ]),
      )
    })

    it('rejects a page parameter equal to a query parameter at both fields', () => {
      const apiCall = { ...withPagination({ pageParameter: 'page' }), queryParameters: [{ name: 'page', value: '1' }] }
      const result = issues(HttpLooseSchema, [apiCall])
      expect(result).toContainEqual(
        expect.objectContaining({
          path: [0, 'pagination', 'pageParameter'],
          message: 'datasources.errors.paginationQueryDuplicate',
        }),
      )
      expect(result).toContainEqual(
        expect.objectContaining({
          path: [0, 'queryParameters', 0, 'name'],
          message: 'datasources.errors.parameterPaginationDuplicate',
        }),
      )
    })

    it('accepts a page parameter equal to a header', () => {
      const apiCall = { ...withPagination({ pageParameter: 'page' }), headers: [{ name: 'page', value: '1' }] }
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it('accepts equal names in a disabled pagination', () => {
      const pagination = { ...EMPTY_PAGINATION, pageParameter: 'page', pageSizeParameter: 'page' }
      const apiCall = { ...API_CALL, queryParameters: [{ name: 'page', value: '1' }], pagination }
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it('accepts a disabled pagination without values for release', () => {
      expect(issues(HttpStrictSchema, [API_CALL])).toEqual([])
    })

    it.each([
      ['loose', HttpLooseSchema],
      ['strict', HttpStrictSchema],
    ] as const)('ignores invalid values of a disabled pagination in the %s schema', (_name, schema) => {
      const pagination = { ...EMPTY_PAGINATION, pageSize: '0', startPage: '5', endPage: '4' }
      expect(issues(schema, [{ ...API_CALL, pagination }])).toEqual([])
    })
  })

  describe('cursor pagination', () => {
    const withCursor = (pagination: Partial<HttpCursorPaginationForm>, apiCall: Partial<HttpApiCallForm> = {}) => ({
      ...API_CALL,
      ...apiCall,
      pagination: { ...EMPTY_CURSOR_PAGINATION, isEnabled: true, ...pagination },
    })
    const issue = (field: string, error: string) =>
      expect.objectContaining({ path: [0, 'pagination', field], message: error })

    it('maps a cursor pagination from the API to the form', () => {
      const result = ConnectorApiToFormSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ path: '/stations', pagination: CURSOR_PAGINATION_API }] },
      })

      expect(result.configuration).toMatchObject({ apiCalls: [{ pagination: CURSOR_PAGINATION_FORM }] })
    })

    it('maps empty cursor values from the API to the form', () => {
      const pagination = {
        type: 'CURSOR',
        cursorPath: null,
        cursorParameter: null,
        cursorIn: null,
        limitParameter: null,
        limit: null,
      }
      const result = ConnectorApiToFormSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ path: '/stations', pagination }] },
      })

      expect(result.configuration).toMatchObject({
        apiCalls: [{ pagination: { ...EMPTY_CURSOR_PAGINATION, isEnabled: true } }],
      })
    })

    it('sends a cursor pagination', () => {
      const result = ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ ...API_CALL, pagination: CURSOR_PAGINATION_FORM }] },
      })

      expect(result).toMatchObject({ apiCalls: [{ pagination: CURSOR_PAGINATION_API }] })
    })

    it('sends empty cursor values as null', () => {
      const result = ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [withCursor({})] },
      })

      expect(result).toMatchObject({
        apiCalls: [
          {
            pagination: {
              type: 'CURSOR',
              cursorPath: null,
              cursorParameter: null,
              cursorIn: 'QUERY',
              limitParameter: null,
              limit: null,
            },
          },
        ],
      })
    })

    it('accepts empty fields in a draft', () => {
      expect(issues(HttpLooseSchema, [withCursor({})])).toEqual([])
    })

    it('accepts a complete cursor pagination for release', () => {
      expect(issues(HttpStrictSchema, [{ ...API_CALL, pagination: CURSOR_PAGINATION_FORM }])).toEqual([])
    })

    it.each(['next_cursor', '$', '$..id', '$[?(@.id)]'])('rejects the cursor path %s', cursorPath => {
      expect(issues(HttpLooseSchema, [withCursor({ cursorPath })])).toContainEqual(
        issue('cursorPath', 'datasources.errors.invalidCursorPath'),
      )
    })

    it.each(['$.next_cursor', '$.data[0].id', "$['next cursor']"])('accepts the cursor path %s', cursorPath => {
      expect(issues(HttpLooseSchema, [withCursor({ cursorPath })])).toEqual([])
    })

    it.each(['cursorPath', 'cursorParameter'])('requires %s for release', field => {
      const apiCall = { ...API_CALL, pagination: { ...CURSOR_PAGINATION_FORM, [field]: '' } }
      expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(issue(field, 'common.errors.required'))
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it.each([
      ['an invalid header name', 'my cursor', 'datasources.errors.invalidHeaderName'],
      ['a credential header', 'Cookie', 'datasources.errors.credentialHeader'],
      ['a header the HTTP client sets', 'Host', 'datasources.errors.clientHeader'],
    ])('rejects %s as cursor header', (_name, cursorParameter, error) => {
      expect(issues(HttpLooseSchema, [withCursor({ cursorParameter, cursorIn: 'HEADER' })])).toContainEqual(
        issue('cursorParameter', error),
      )
    })

    it.each(['my cursor', 'Cookie', 'Host'])('accepts %s as cursor query parameter', cursorParameter => {
      expect(issues(HttpLooseSchema, [withCursor({ cursorParameter })])).toEqual([])
    })

    it.each([
      [
        'a cursor query parameter equal to a query parameter',
        withCursor({ cursorParameter: 'cursor' }, { queryParameters: [{ name: 'cursor', value: 'x' }] }),
        'cursorParameter',
        'datasources.errors.paginationQueryDuplicate',
      ],
      [
        'a cursor header equal to a header in other case',
        withCursor(
          { cursorParameter: 'X-Cursor', cursorIn: 'HEADER' },
          { headers: [{ name: 'x-cursor', value: 'x' }] },
        ),
        'cursorParameter',
        'datasources.errors.paginationHeaderDuplicate',
      ],
      [
        'a limit parameter equal to a query parameter',
        withCursor({ limitParameter: 'limit' }, { queryParameters: [{ name: 'limit', value: '5' }] }),
        'limitParameter',
        'datasources.errors.paginationQueryDuplicate',
      ],
      [
        'a limit parameter equal to the cursor query parameter',
        withCursor({ cursorParameter: 'cursor', limitParameter: 'cursor' }),
        'limitParameter',
        'datasources.errors.paginationParameterDuplicate',
      ],
    ])('rejects %s', (_name, apiCall, field, error) => {
      expect(issues(HttpLooseSchema, [apiCall])).toContainEqual(issue(field, error))
    })

    it.each([
      [
        'a query parameter equal to the cursor query parameter',
        withCursor({ cursorParameter: 'cursor' }, { queryParameters: [{ name: 'cursor', value: 'x' }] }),
        [0, 'queryParameters', 0, 'name'],
      ],
      [
        'a query parameter after an empty row',
        withCursor(
          { cursorParameter: 'cursor' },
          {
            queryParameters: [
              { name: '', value: '' },
              { name: 'cursor', value: 'x' },
            ],
          },
        ),
        [0, 'queryParameters', 1, 'name'],
      ],
      [
        'a header equal to the cursor header in other case',
        withCursor(
          { cursorParameter: 'X-Cursor', cursorIn: 'HEADER' },
          { headers: [{ name: 'x-cursor', value: 'x' }] },
        ),
        [0, 'headers', 0, 'name'],
      ],
      [
        'a query parameter equal to the limit parameter',
        withCursor(
          { cursorParameter: 'cursor', limitParameter: 'limit' },
          { queryParameters: [{ name: 'limit', value: '5' }] },
        ),
        [0, 'queryParameters', 0, 'name'],
      ],
    ])('rejects %s also at the row', (_name, apiCall, path) => {
      expect(issues(HttpLooseSchema, [apiCall])).toContainEqual(
        expect.objectContaining({ path, message: 'datasources.errors.parameterPaginationDuplicate' }),
      )
    })

    it('rejects a query parameter after an empty row also at the row for release', () => {
      const apiCall = {
        ...API_CALL,
        queryParameters: [
          { name: '', value: '' },
          { name: 'cursor', value: 'x' },
        ],
        pagination: { ...CURSOR_PAGINATION_FORM, cursorParameter: 'cursor' },
      }
      expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(
        expect.objectContaining({
          path: [0, 'queryParameters', 1, 'name'],
          message: 'datasources.errors.parameterPaginationDuplicate',
        }),
      )
    })

    it('rejects a limit parameter that breaks two rules', () => {
      const apiCall = withCursor(
        { cursorParameter: 'cursor', limitParameter: 'cursor' },
        { queryParameters: [{ name: 'cursor', value: 'x' }] },
      )
      const result = issues(HttpLooseSchema, [apiCall])
      expect(result).toEqual(
        expect.arrayContaining([
          issue('limitParameter', 'datasources.errors.paginationQueryDuplicate'),
          issue('limitParameter', 'datasources.errors.paginationParameterDuplicate'),
          issue('cursorParameter', 'datasources.errors.paginationParameterDuplicate'),
        ]),
      )
      const duplicates = result.filter(
        resultIssue => resultIssue.message === 'datasources.errors.paginationParameterDuplicate',
      )
      expect(duplicates).toHaveLength(2)
    })

    it.each([
      [
        'a cursor query parameter equal to a header',
        withCursor({ cursorParameter: 'cursor' }, { headers: [{ name: 'cursor', value: 'x' }] }),
      ],
      [
        'a limit parameter equal to the cursor header',
        withCursor({ cursorParameter: 'cursor', cursorIn: 'HEADER', limitParameter: 'cursor' }),
      ],
    ])('accepts %s', (_name, apiCall) => {
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it.each([
      ['abc', 'limitInteger'],
      ['-1', 'limitInteger'],
      ['1.5', 'limitInteger'],
      ['0', 'limitInteger'],
      ['2147483648', 'pageNumberMax'],
    ])('rejects the limit %s', (limit, error) => {
      expect(issues(HttpLooseSchema, [withCursor({ limit })])).toContainEqual(
        issue('limit', `datasources.errors.${error}`),
      )
    })

    it('accepts the limit 1', () => {
      expect(issues(HttpLooseSchema, [withCursor({ limit: '1' })])).toEqual([])
    })

    it.each([
      [
        'a limit without a limit parameter',
        { limitParameter: '', limit: '20' },
        'limitParameter',
        'limitParameterRequired',
      ],
      ['a limit parameter without a limit', { limitParameter: 'limit', limit: '' }, 'limit', 'limitRequired'],
    ])('rejects %s for release only', (_name, pagination, field, error) => {
      const apiCall = { ...API_CALL, pagination: { ...CURSOR_PAGINATION_FORM, ...pagination } }
      expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(issue(field, `datasources.errors.${error}`))
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    const DISABLED_CURSOR_CASES = [
      [
        'a header cursor',
        withCursor(
          { isEnabled: false, cursorPath: 'next', cursorParameter: 'Host', cursorIn: 'HEADER', limit: '0' },
          { headers: [{ name: 'host', value: 'x' }] },
        ),
      ],
      [
        'a query cursor',
        withCursor(
          { isEnabled: false, cursorParameter: 'cursor', limitParameter: 'cursor' },
          { queryParameters: [{ name: 'cursor', value: 'x' }] },
        ),
      ],
    ] as const

    describe.each([
      ['loose', HttpLooseSchema],
      ['strict', HttpStrictSchema],
    ] as const)('in the %s schema', (_name, schema) => {
      it.each(DISABLED_CURSOR_CASES)('ignores invalid values of a disabled pagination with %s', (_case, apiCall) => {
        expect(issues(schema, [apiCall]).filter(({ path }) => path.includes('pagination'))).toEqual([])
      })
    })
  })

  describe('limit/offset pagination', () => {
    const withLimitOffset = (
      pagination: Partial<HttpLimitOffsetPaginationForm>,
      apiCall: Partial<HttpApiCallForm> = {},
    ) => ({
      ...API_CALL,
      ...apiCall,
      pagination: { ...EMPTY_LIMIT_OFFSET_PAGINATION, isEnabled: true, ...pagination },
    })
    const issue = (field: string, error: string) =>
      expect.objectContaining({ path: [0, 'pagination', field], message: error })

    it('maps a limit/offset pagination from the API to the form', () => {
      const result = ConnectorApiToFormSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ path: '/items', pagination: LIMIT_OFFSET_PAGINATION_API }] },
      })

      expect(result.configuration).toMatchObject({ apiCalls: [{ pagination: LIMIT_OFFSET_PAGINATION_FORM }] })
    })

    it('maps empty limit/offset values from the API to the form with offset 0', () => {
      const pagination = {
        type: 'LIMIT_OFFSET',
        offsetParameter: null,
        offset: null,
        limitParameter: null,
        limit: null,
        countPath: null,
      }
      const result = ConnectorApiToFormSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ path: '/items', pagination }] },
      })

      expect(result.configuration).toMatchObject({
        apiCalls: [{ pagination: { ...EMPTY_LIMIT_OFFSET_PAGINATION, isEnabled: true } }],
      })
    })

    it('sends a limit/offset pagination', () => {
      const result = ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [{ ...API_CALL, pagination: LIMIT_OFFSET_PAGINATION_FORM }] },
      })

      expect(result).toMatchObject({ apiCalls: [{ pagination: LIMIT_OFFSET_PAGINATION_API }] })
    })

    it('sends empty limit/offset values as null and the default offset as 0', () => {
      const result = ConnectorFormToApiSchema.parse({
        connectorType: CONNECTOR_TYPES.HTTP,
        configuration: { apiCalls: [withLimitOffset({})] },
      })

      expect(result).toMatchObject({
        apiCalls: [
          {
            pagination: {
              type: 'LIMIT_OFFSET',
              offsetParameter: null,
              offset: 0,
              limitParameter: null,
              limit: null,
              countPath: null,
            },
          },
        ],
      })
    })

    it('accepts empty fields in a draft', () => {
      expect(issues(HttpLooseSchema, [withLimitOffset({})])).toEqual([])
    })

    it('accepts a complete limit/offset pagination for release', () => {
      expect(issues(HttpStrictSchema, [{ ...API_CALL, pagination: LIMIT_OFFSET_PAGINATION_FORM }])).toEqual([])
    })

    it('accepts a limit/offset pagination with only the offset parameter for release', () => {
      expect(issues(HttpStrictSchema, [withLimitOffset({ offsetParameter: 'offset' })])).toEqual([])
    })

    it('requires the offset parameter for release only', () => {
      const apiCall = { ...API_CALL, pagination: { ...LIMIT_OFFSET_PAGINATION_FORM, offsetParameter: '' } }
      expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(issue('offsetParameter', 'common.errors.required'))
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it.each([
      ['abc', 'pageNumberInteger'],
      ['-1', 'pageNumberInteger'],
      ['1.5', 'pageNumberInteger'],
      ['2147483648', 'pageNumberMax'],
    ])('rejects the offset %s', (offset, error) => {
      expect(issues(HttpLooseSchema, [withLimitOffset({ offset })])).toContainEqual(
        issue('offset', `datasources.errors.${error}`),
      )
    })

    it.each(['0', ''])('accepts the offset %j', offset => {
      expect(issues(HttpLooseSchema, [withLimitOffset({ offset })])).toEqual([])
    })

    it.each(['abc', '0'])('rejects the limit %s', limit => {
      expect(issues(HttpLooseSchema, [withLimitOffset({ limit })])).toContainEqual(
        issue('limit', 'datasources.errors.limitInteger'),
      )
    })

    it('accepts the limit 1', () => {
      expect(issues(HttpLooseSchema, [withLimitOffset({ limit: '1' })])).toEqual([])
    })

    it.each([
      [
        'a limit without a limit parameter',
        { limitParameter: '', limit: '20' },
        'limitParameter',
        'limitParameterRequired',
      ],
      ['a limit parameter without a limit', { limitParameter: 'limit', limit: '' }, 'limit', 'limitRequired'],
    ])('rejects %s for release only', (_name, pagination, field, error) => {
      const apiCall = { ...API_CALL, pagination: { ...LIMIT_OFFSET_PAGINATION_FORM, ...pagination } }
      expect(issues(HttpStrictSchema, [apiCall])).toContainEqual(issue(field, `datasources.errors.${error}`))
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    it.each(['meta.count', '$', '$..count', '$[?(@.n)]'])('rejects the count path %s', countPath => {
      expect(issues(HttpLooseSchema, [withLimitOffset({ countPath })])).toContainEqual(
        issue('countPath', 'datasources.errors.invalidCountPath'),
      )
    })

    it.each(['$.meta.count', '$.data', '$.data[0].total', "$['total count']"])(
      'accepts the count path %s',
      countPath => {
        expect(issues(HttpLooseSchema, [withLimitOffset({ countPath })])).toEqual([])
      },
    )

    it.each([
      ['offset parameter', 'offsetParameter', { offsetParameter: 'offset' }, 'offset'],
      ['limit parameter', 'limitParameter', { limitParameter: 'limit' }, 'limit'],
    ])('rejects a %s equal to a query parameter', (_name, field, pagination, name) => {
      const apiCall = withLimitOffset(pagination, { queryParameters: [{ name, value: '5' }] })
      expect(issues(HttpLooseSchema, [apiCall])).toEqual(
        expect.arrayContaining([
          issue(field, 'datasources.errors.paginationQueryDuplicate'),
          expect.objectContaining({
            path: [0, 'queryParameters', 0, 'name'],
            message: 'datasources.errors.parameterPaginationDuplicate',
          }),
        ]),
      )
    })

    it('rejects an offset parameter equal to the limit parameter once at each field', () => {
      const result = issues(HttpLooseSchema, [withLimitOffset({ offsetParameter: 'page', limitParameter: 'page' })])
      const duplicates = result.filter(
        resultIssue => resultIssue.message === 'datasources.errors.paginationParameterDuplicate',
      )
      const paths = duplicates.map(resultIssue => resultIssue.path)
      expect(paths).toHaveLength(2)
      expect(paths).toEqual(
        expect.arrayContaining([
          [0, 'pagination', 'offsetParameter'],
          [0, 'pagination', 'limitParameter'],
        ]),
      )
    })

    it('accepts an offset parameter equal to a header', () => {
      const apiCall = withLimitOffset({ offsetParameter: 'offset' }, { headers: [{ name: 'offset', value: 'x' }] })
      expect(issues(HttpLooseSchema, [apiCall])).toEqual([])
    })

    describe.each([
      ['loose', HttpLooseSchema],
      ['strict', HttpStrictSchema],
    ] as const)('in the %s schema', (_name, schema) => {
      it('ignores invalid values of a disabled pagination', () => {
        const apiCall = withLimitOffset(
          { isEnabled: false, offsetParameter: 'offset', offset: '-1', limitParameter: 'offset', countPath: 'x' },
          { queryParameters: [{ name: 'offset', value: 'x' }] },
        )
        expect(issues(schema, [apiCall])).toEqual([])
      })
    })
  })
})
