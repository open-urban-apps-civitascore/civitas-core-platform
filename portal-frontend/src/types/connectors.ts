/* eslint-disable @typescript-eslint/naming-convention */
import { z } from 'zod'

import {
  CONNECTOR_TYPES,
  HTTP_AUTHENTICATION_TYPES,
  HTTP_METHODS,
  HTTP_PARAMETER_LOCATIONS,
  SECRET_PLACEHOLDER,
} from '@/const/connectors'
import {
  getApiTokenCollisionPaths,
  getHeaderNameError,
  HttpApiCallApiResponseSchema,
  HttpApiCallsLooseSchema,
  HttpApiCallsStrictSchema,
  requireFields,
} from '@/types/httpApiCalls'
import { isValidUri } from '@/utils/common'
import {
  removeEmptyParameters,
  toAuthenticationApi,
  toAuthenticationForm,
  toPaginationApi,
} from '@/utils/connectorMappers'

// Matches the URL rules of java.net.URI in the backend.
const HTTP_URL_PARTS = /^https?:\/\/(?:[^@/?#]*@)?(\[[^\]]*\]|[^:/?#]*)(.*)$/i
const DOMAIN_NAME = /^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)*[a-z](?:[a-z0-9-]*[a-z0-9])?\.?$/i
const IPV4_ADDRESS = /^\d{1,3}(?:\.\d{1,3}){3}$/
const IPV6_ADDRESS = /^\[[0-9a-f:.]+\]$/i
const AFTER_HOST = /^[A-Za-z0-9\-._~!$&'()*+,;=:@/%\u0080-\uFFFF]*$/
const INVALID_PERCENT_ESCAPE = /%(?![0-9a-f]{2})/i
const SPACE_OR_CONTROL = /[\p{Z}\p{Cc}]/u

const TRAILING_SLASHES = /\/+$/
const SINGLE_MQTT_TOPIC = /^[^,;\s]*$/
const PEM_CERTIFICATES =
  /^-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----(?:\s+-----BEGIN CERTIFICATE-----[^-]+-----END CERTIFICATE-----)*$/
const ENCRYPTED_PRIVATE_KEY = '-----BEGIN ENCRYPTED PRIVATE KEY-----'
const ENCRYPTED_PEM_HEADER = 'Proc-Type: 4,ENCRYPTED'
const PRIVATE_KEYS = ['-----BEGIN PRIVATE KEY-----', '-----BEGIN RSA PRIVATE KEY-----']
const HTTP_SCHEME = /^http:/i
const HTTPS_SCHEME = /^https:/i
const QUERY_OR_FRAGMENT = /[?#]/

export type ConnectorTypeKey = keyof typeof CONNECTOR_TYPES

export type ConnectorType = (typeof CONNECTOR_TYPES)[keyof typeof CONNECTOR_TYPES]

export type HttpMethod = (typeof HTTP_METHODS)[keyof typeof HTTP_METHODS]

const QosSchema = z.preprocess(
  v => (typeof v === 'string' ? Number(v) : v),
  z.union([z.literal(0), z.literal(1), z.literal(2)]),
)

const MqttProtocolVersionSchema = z.enum(['3', '5'])

export const parseStringArray = (v: unknown): string[] | undefined => {
  if (v === undefined) return undefined
  if (typeof v === 'string') {
    const value = v
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
    return value
  }
  return undefined
}

export const stringifyStringArray = (v: unknown): string | undefined => {
  if (v === undefined) return undefined
  if (Array.isArray(v) && v.every(s => typeof s === 'string')) {
    return v
      .map(s => s.trim())
      .filter(Boolean)
      .join(',')
  }
  return undefined
}

export const MqttApiResponseSchema = z.object({
  urls: z.array(z.string()).nullable().optional(),
  topics: z.array(z.string()).max(1).nullable().optional(),
  qos: QosSchema.nullable().optional(),
  protocol_version: MqttProtocolVersionSchema,
  connect_timeout: z.string().nullable().optional(),
  keepalive: z.string().nullable().optional(),
  tls: z.object({ enabled: z.boolean() }).nullable().optional(),
  user: z.string().nullable().optional(),
  password: z.string().nullable().optional(),
})

/* Form schemas for edit */
const singleTopic = z.string().regex(SINGLE_MQTT_TOPIC, 'datasources.errors.topicInvalidChars')

const MqttBaseSchema = z.object({
  urls: z.string().trim(),
  topics: singleTopic,
  qos: QosSchema,
  protocol_version: MqttProtocolVersionSchema,
  connect_timeout: z.string().trim(),
  keepalive: z.string().trim(),
  tls: z.boolean(),
  user: z.string().trim(),
  password: z.string().trim(),
})

const uriSchema = (message: string) =>
  z.string().refine(v => {
    if (!v) return true
    return isValidUri(v)
  }, message)

const URISchema = uriSchema('datasources.errors.invalidDsn')

const brokerUrlArray = (inner: z.ZodTypeAny) =>
  z
    .preprocess(parseStringArray, inner)
    .refine(urls => !urls || (urls as string[]).every(isValidUri), 'datasources.errors.invalidBrokerUrl')

const MQTT_PLAINTEXT_SCHEMES = new Set(['tcp:', 'ws:', 'mqtt:'])
const MQTT_TLS_SCHEMES = new Set(['ssl:', 'mqtts:', 'wss:'])

const validateMqttSchemes = (configuration: { urls?: unknown; tls?: boolean }, ctx: z.RefinementCtx) => {
  const urls = Array.isArray(configuration.urls)
    ? configuration.urls.filter((url): url is string => typeof url === 'string')
    : []
  for (const value of urls) {
    if (!isValidUri(value)) continue
    const scheme = new URL(value).protocol.toLowerCase()
    if (!MQTT_PLAINTEXT_SCHEMES.has(scheme) && !MQTT_TLS_SCHEMES.has(scheme)) {
      ctx.addIssue({
        code: 'custom',
        path: ['urls'],
        message: 'datasources.errors.unsupportedBrokerScheme',
      })
      return
    }
    const schemeUsesTls = MQTT_TLS_SCHEMES.has(scheme)
    if (schemeUsesTls !== (configuration.tls ?? false)) {
      ctx.addIssue({
        code: 'custom',
        path: ['urls'],
        message: 'datasources.errors.brokerTlsMismatch',
      })
      return
    }
  }
}

export type FieldRevalidationRule = { watch: string; revalidate: string[] }

/**
 * For each connector type, declares configuration fields whose change should re-trigger
 * validation of other fields (e.g. toggling MQTT's `tls` must re-validate `urls`, since
 * `validateMqttSchemes` above reads `configuration.tls` when validating `configuration.urls`).
 * Colocating this with the schema that owns the cross-field relationship keeps it a single
 * source of truth. An empty list means the connector type has no such rule.
 */
export const CONNECTOR_FIELD_REVALIDATION_RULES: Record<ConnectorType, FieldRevalidationRule[]> = {
  [CONNECTOR_TYPES.MQTT]: [{ watch: 'tls', revalidate: ['urls'] }],
  [CONNECTOR_TYPES.SQL]: [],
  [CONNECTOR_TYPES.HTTP]: [
    { watch: 'authentication.in', revalidate: ['authentication.name', 'apiCalls'] },
    { watch: 'authentication.name', revalidate: ['apiCalls'] },
  ],
}

export const MqttLooseSchema = MqttBaseSchema.partial()
  .extend({
    urls: brokerUrlArray(z.array(z.string()).optional()),
    topics: singleTopic
      .optional()
      .transform(parseStringArray)
      .pipe(z.array(z.string()).max(1, 'datasources.errors.topicSingle').optional()),
  })
  .superRefine(validateMqttSchemes)

export const MqttStrictSchema = MqttBaseSchema.partial()
  .required({ qos: true, protocol_version: true })
  .extend({
    urls: brokerUrlArray(z.array(z.string()).min(1, 'common.errors.required')),
    topics: singleTopic
      .transform(v => parseStringArray(v) ?? [])
      .pipe(z.array(z.string()).min(1, 'common.errors.required').max(1, 'datasources.errors.topicSingle')),
  })
  .superRefine(validateMqttSchemes)

export const MqttApiToFormSchema = MqttApiResponseSchema.transform(
  ({ urls, topics, tls, qos, protocol_version, ...rest }) => ({
    ...Object.fromEntries(
      Object.entries(rest).map(([k, v]) => [
        k,
        typeof v === 'string' && v.trim() === '' ? undefined : (v ?? undefined),
      ]),
    ),
    urls: urls?.join(', '),
    topics: topics?.join(', '),
    qos: qos == null ? undefined : String(qos),
    protocol_version,
    tls: tls?.enabled ?? false,
  }),
)

export const SqlApiResponseSchema = z.object({
  driver: z
    .enum(['postgres', 'mysql', 'clickhouse', 'mssql', 'sqlite', 'oracle', 'snowflake', 'trino', 'gocosmos', 'spanner'])
    .nullable(),
  dsn: z.string().nullable(),
  table: z.string().nullable(),
  columns: z.array(z.string()).nullable(),
  where: z.string().nullable(),
  user: z.string().nullable(),
  password: z.string().nullable(),
})

const SqlBaseSchema = z.object({
  // Only PostgreSQL is wired in the NiFi pipeline engine, so the form offers no other driver. The
  // wide driver list stays in SqlApiResponseSchema so loading a legacy datasource that still names
  // another driver does not fail.
  driver: z.literal('postgres'),
  dsn: z.string().trim(),
  table: z.string().trim(),
  columns: z.string().trim(),
  where: z.string().trim(),
  user: z.string().trim(),
  password: z.string().trim(),
})

export const SqlLooseSchema = SqlBaseSchema.partial().extend({
  dsn: URISchema.optional(),
  columns: z.preprocess(parseStringArray, z.array(z.string()).optional()),
})

export const SqlStrictSchema = SqlBaseSchema.partial()
  .required({
    driver: true,
    dsn: true,
    table: true,
    columns: true,
  })
  .extend({
    dsn: z.string().min(1, 'common.errors.required').pipe(URISchema),
    table: z.string().trim().min(1, 'common.errors.descriptionRequired'),
    columns: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
  })

export const SqlApiToFormSchema = SqlApiResponseSchema.transform(({ columns, ...rest }) => ({
  ...Object.fromEntries(Object.entries(rest).map(([k, v]) => [k, v ?? undefined])),
  columns: columns?.join(', '),
}))

const isValidHost = (host: string) => DOMAIN_NAME.test(host) || IPV4_ADDRESS.test(host) || IPV6_ADDRESS.test(host)

const isValidHttpBaseUrl = (value: string) => {
  const [, host, afterHost] = HTTP_URL_PARTS.exec(value) ?? []
  if (!host || !AFTER_HOST.test(afterHost) || SPACE_OR_CONTROL.test(afterHost)) return false
  return !INVALID_PERCENT_ESCAPE.test(value) && isValidUri(value) && isValidHost(host)
}

const hasUrlCredentials = (value: string) => {
  if (!isValidUri(value)) return false
  const url = new URL(value)
  return url.username !== '' || url.password !== ''
}

export const normalizeHttpBaseUrl = (value: string) => value.trim().replace(TRAILING_SLASHES, '')

const HttpBaseUrlSchema = z
  .string()
  .trim()
  .refine(v => !v || isValidHttpBaseUrl(v), 'datasources.errors.invalidBaseUrl')
  .refine(v => !hasUrlCredentials(v), 'datasources.errors.baseUrlCredentials')
  .transform(normalizeHttpBaseUrl)

const HttpAuthenticationApiResponseSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal(HTTP_AUTHENTICATION_TYPES.NONE) }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.BASIC),
    username: z.string().nullable().optional(),
    password: z.string().nullable().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN),
    token: z.string().nullable().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.API_TOKEN),
    name: z.string().nullable().optional(),
    value: z.string().nullable().optional(),
    in: z.enum(HTTP_PARAMETER_LOCATIONS).nullable().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.OAUTH2),
    tokenUrl: z.string().nullable().optional(),
    clientId: z.string().nullable().optional(),
    clientSecret: z.string().nullable().optional(),
    scope: z.string().nullable().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE),
    certificate: z.string().nullable().optional(),
    privateKey: z.string().nullable().optional(),
  }),
])

export type HttpAuthenticationApiResponse = z.infer<typeof HttpAuthenticationApiResponseSchema>

export const HttpApiResponseSchema = z.object({
  baseUrl: z.string().nullable().optional(),
  authentication: HttpAuthenticationApiResponseSchema.nullable().optional(),
  apiCalls: z.array(HttpApiCallApiResponseSchema).nullable().optional(),
})

const HttpAuthenticationFormSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal(HTTP_AUTHENTICATION_TYPES.NONE) }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.BASIC),
    username: z.string().trim().optional(),
    password: z.string().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN),
    token: z.string().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.API_TOKEN),
    name: z.string().trim().optional(),
    value: z.string().optional(),
    in: z.enum(HTTP_PARAMETER_LOCATIONS),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.OAUTH2),
    tokenUrl: z.string().trim().optional(),
    clientId: z.string().trim().optional(),
    clientSecret: z.string().optional(),
    scope: z.string().trim().optional(),
  }),
  z.object({
    type: z.literal(HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE),
    certificate: z.string().trim().optional(),
    privateKey: z.string().trim().optional(),
  }),
])

export type HttpAuthenticationForm = z.infer<typeof HttpAuthenticationFormSchema>

export type HttpAuthenticationType = (typeof HTTP_AUTHENTICATION_TYPES)[keyof typeof HTTP_AUTHENTICATION_TYPES]

export const HttpApiToFormSchema = HttpApiResponseSchema.transform(({ baseUrl, authentication, apiCalls }) => ({
  baseUrl: baseUrl ?? undefined,
  authentication: toAuthenticationForm(authentication),
  apiCalls: apiCalls ?? [],
}))

const getTokenUrlError = (tokenUrl: string) => {
  if (QUERY_OR_FRAGMENT.test(tokenUrl)) return 'datasources.errors.tokenUrlQueryOrFragment'
  if (!isValidHttpBaseUrl(tokenUrl)) return 'datasources.errors.invalidTokenUrl'
  if (!HTTPS_SCHEME.test(tokenUrl)) return 'datasources.errors.tokenUrlHttps'
  if (hasUrlCredentials(tokenUrl)) return 'datasources.errors.tokenUrlCredentials'
  return undefined
}

// The config-adapter cannot read an encrypted private key
const getPrivateKeyError = (privateKey: string) => {
  const isEncrypted = privateKey.startsWith(ENCRYPTED_PRIVATE_KEY) || privateKey.includes(ENCRYPTED_PEM_HEADER)
  if (isEncrypted) return 'datasources.errors.encryptedPrivateKey'
  if (!PRIVATE_KEYS.some(header => privateKey.startsWith(header))) return 'datasources.errors.invalidPrivateKey'
  return undefined
}

const validateAuthentication = (authentication: HttpAuthenticationForm, ctx: z.RefinementCtx) => {
  const addIssue = (field: string, message: string) => ctx.addIssue({ code: 'custom', path: [field], message })
  switch (authentication.type) {
    case HTTP_AUTHENTICATION_TYPES.BASIC:
      if (authentication.username?.includes(':')) {
        addIssue('username', 'datasources.errors.usernameColon')
      }
      break
    case HTTP_AUTHENTICATION_TYPES.API_TOKEN: {
      const { name } = authentication
      const nameError =
        name && authentication.in === HTTP_PARAMETER_LOCATIONS.HEADER ? getHeaderNameError(name, true) : undefined
      if (nameError) {
        addIssue('name', nameError)
      }
      break
    }
    case HTTP_AUTHENTICATION_TYPES.OAUTH2: {
      const tokenUrlError = authentication.tokenUrl ? getTokenUrlError(authentication.tokenUrl) : undefined
      if (tokenUrlError) {
        addIssue('tokenUrl', tokenUrlError)
      }
      break
    }
    case HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE: {
      const { certificate, privateKey } = authentication
      if (certificate && certificate !== SECRET_PLACEHOLDER && !PEM_CERTIFICATES.test(certificate)) {
        addIssue('certificate', 'datasources.errors.invalidCertificate')
      }
      const privateKeyError =
        privateKey && privateKey !== SECRET_PLACEHOLDER ? getPrivateKeyError(privateKey) : undefined
      if (privateKeyError) {
        addIssue('privateKey', privateKeyError)
      }
      break
    }
  }
}

const requireAuthentication = (authentication: HttpAuthenticationForm, ctx: z.RefinementCtx) => {
  validateAuthentication(authentication, ctx)
  switch (authentication.type) {
    case HTTP_AUTHENTICATION_TYPES.BASIC:
      requireFields(authentication, ['username', 'password'], ctx)
      break
    case HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN:
      requireFields(authentication, ['token'], ctx)
      break
    case HTTP_AUTHENTICATION_TYPES.API_TOKEN:
      requireFields(authentication, ['name', 'value', 'in'], ctx)
      break
    case HTTP_AUTHENTICATION_TYPES.OAUTH2:
      requireFields(authentication, ['tokenUrl', 'clientId', 'clientSecret'], ctx)
      break
    case HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE:
      requireFields(authentication, ['certificate', 'privateKey'], ctx)
      break
  }
}

const HttpLooseBaseSchema = z.object({
  baseUrl: HttpBaseUrlSchema.optional(),
  authentication: HttpAuthenticationFormSchema.superRefine(validateAuthentication).optional(),
  apiCalls: HttpApiCallsLooseSchema.optional(),
})

type HttpConfigurationOutput = z.output<typeof HttpLooseBaseSchema>

const validateHttpAuthenticationContext = (configuration: HttpConfigurationOutput, ctx: z.RefinementCtx) => {
  const { baseUrl, authentication, apiCalls } = configuration
  if (!authentication) return
  if (authentication.type !== HTTP_AUTHENTICATION_TYPES.NONE && baseUrl && HTTP_SCHEME.test(baseUrl)) {
    ctx.addIssue({ code: 'custom', path: ['baseUrl'], message: 'datasources.errors.baseUrlHttpsRequired' })
  }
  if (authentication.type !== HTTP_AUTHENTICATION_TYPES.API_TOKEN || !authentication.name) return
  const token = { name: authentication.name, location: authentication.in }
  const collisionPaths = (apiCalls ?? []).flatMap((apiCall, index) =>
    getApiTokenCollisionPaths(token, apiCall).map(path => ['apiCalls', index, ...path]),
  )
  if (!collisionPaths.length) return
  ctx.addIssue({
    code: 'custom',
    path: ['authentication', 'name'],
    message: 'datasources.errors.apiTokenNameCollision',
  })
  for (const path of collisionPaths) {
    ctx.addIssue({ code: 'custom', path, message: 'datasources.errors.parameterApiTokenDuplicate' })
  }
}

export const HttpLooseSchema = HttpLooseBaseSchema.superRefine(validateHttpAuthenticationContext)

const HttpStrictConnectionSchema = z.object({
  baseUrl: z.string().trim().min(1, 'common.errors.required').pipe(HttpBaseUrlSchema),
  authentication: HttpAuthenticationFormSchema.superRefine(requireAuthentication),
})

export const HttpStrictSchema = HttpStrictConnectionSchema.extend({ apiCalls: HttpApiCallsStrictSchema }).superRefine(
  validateHttpAuthenticationContext,
)

export type HttpConnectionForm = { baseUrl?: string; authentication?: HttpAuthenticationForm }

const HttpFormToApiSchema = HttpLooseSchema.transform(({ authentication, apiCalls, ...rest }) => ({
  ...rest,
  apiCalls: apiCalls?.map(apiCall => ({
    ...removeEmptyParameters(apiCall),
    pagination: toPaginationApi(apiCall.pagination),
  })),
  authentication: toAuthenticationApi(authentication),
}))

const MqttFormToApiSchema = MqttLooseSchema.transform(({ tls, ...rest }) => ({
  ...rest,
  tls: { enabled: tls ?? false },
}))

/** Factory for connector discriminated unions — add new connector types in one place */
const connectorUnion = <T extends Record<ConnectorType, z.ZodTypeAny>>(schemas: T) =>
  z.discriminatedUnion('connectorType', [
    z.object({ connectorType: z.literal(CONNECTOR_TYPES.MQTT), configuration: schemas.MQTT }),
    z.object({ connectorType: z.literal(CONNECTOR_TYPES.SQL), configuration: schemas.SQL }),
    z.object({ connectorType: z.literal(CONNECTOR_TYPES.HTTP), configuration: schemas.HTTP }),
  ])

// Keeps the type's fields unvalidated, so a connector the form schemas reject does not stop the Data
// source from loading; ConnectorApiToFormSchema validates them per type.
export const ConnectorApiResponseSchema = z.looseObject({ connectorType: z.enum(CONNECTOR_TYPES) })

export type ConnectorApiResponse = z.infer<typeof ConnectorApiResponseSchema>

export const toConnectorFormShape = ({ connectorType, ...configuration }: ConnectorApiResponse) => ({
  connectorType,
  configuration,
})

export const ConnectorLooseSchema = connectorUnion({
  MQTT: MqttLooseSchema,
  SQL: SqlLooseSchema,
  HTTP: HttpLooseSchema,
})
export const ConnectorStrictSchema = connectorUnion({
  MQTT: MqttStrictSchema,
  SQL: SqlStrictSchema,
  HTTP: HttpStrictSchema,
})
export const ConnectorTabCompleteSchema = connectorUnion({
  MQTT: MqttStrictSchema,
  SQL: SqlStrictSchema,
  HTTP: HttpStrictConnectionSchema.superRefine(validateHttpAuthenticationContext),
})
export const ConnectorApiToFormSchema = connectorUnion({
  MQTT: MqttApiToFormSchema,
  SQL: SqlApiToFormSchema,
  HTTP: HttpApiToFormSchema,
})

/** Maps the form's `connectorType` and `configuration` to the API `connector` member. */
export const ConnectorFormToApiSchema = connectorUnion({
  MQTT: MqttFormToApiSchema,
  SQL: SqlLooseSchema,
  HTTP: HttpFormToApiSchema,
}).transform(({ connectorType, configuration }) => ({ connectorType, ...(configuration as Record<string, unknown>) }))

export type ConnectorDraft = z.infer<typeof ConnectorLooseSchema>
