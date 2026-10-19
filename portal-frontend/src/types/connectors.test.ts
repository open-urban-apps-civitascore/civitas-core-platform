import { CONNECTOR_TYPES } from '@/const/connectors'
import { PAGINATION_API, PAGINATION_FORM } from '@/test-support/paginationFixtures'

import {
  ConnectorApiToFormSchema,
  ConnectorFormToApiSchema,
  EMPTY_PAGINATION,
  HttpLooseSchema,
  HttpPaginationForm,
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

  it('maps a missing authentication from the API to None with empty credentials', () => {
    const result = ConnectorApiToFormSchema.parse({
      connectorType: CONNECTOR_TYPES.HTTP,
      configuration: { baseUrl: 'https://api.example.org/v1', authentication: null, apiCalls: null },
    })

    expect(result.configuration).toEqual({
      baseUrl: 'https://api.example.org/v1',
      authentication: NONE,
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
          path: '/stations',
          queryParameters: [{ name: 'status', value: '' }],
          headers: [],
          pagination: PAGINATION_FORM,
        },
      ],
    })
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
})
