/* eslint-disable @typescript-eslint/naming-convention */
import { z } from 'zod'

import {
  API_CALL_SECTIONS,
  CONNECTOR_TYPES,
  DEFAULT_START_PAGE,
  HTTP_AUTHENTICATION_TYPES,
  HTTP_METHODS,
  HTTP_PAGINATION_TYPES,
  HTTP_PARAMETER_LOCATIONS,
  MAX_HTTP_API_CALLS,
} from '@/const/connectors'

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
const singleTopic = z.string().regex(/^[^,;\s]*$/, 'datasources.errors.topicInvalidChars')

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

const isValidUri = (v: string) => {
  try {
    new URL(v)
    return true
  } catch {
    return false
  }
}

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

export type FieldRevalidationRule = { watch: string; revalidate: string }

/**
 * For each connector type, declares a configuration field whose change should re-trigger
 * validation of another field (e.g. toggling MQTT's `tls` must re-validate `urls`, since
 * `validateMqttSchemes` above reads `configuration.tls` when validating `configuration.urls`).
 * Colocating this with the schema that owns the cross-field relationship keeps it a single
 * source of truth. `null` means the connector type has no such rule.
 */
export const CONNECTOR_FIELD_REVALIDATION_RULES: Record<ConnectorType, FieldRevalidationRule | null> = {
  [CONNECTOR_TYPES.MQTT]: { watch: 'tls', revalidate: 'urls' },
  [CONNECTOR_TYPES.SQL]: null,
  [CONNECTOR_TYPES.HTTP]: { watch: 'authentication.type', revalidate: 'authentication' },
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

// Matches the URL rules of java.net.URI in the backend.
const HTTP_URL_PARTS = /^https?:\/\/(?:[^@/?#]*@)?(\[[^\]]*\]|[^:/?#]*)(.*)$/i
const DOMAIN_NAME = /^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)*[a-z](?:[a-z0-9-]*[a-z0-9])?\.?$/i
const IPV4_ADDRESS = /^\d{1,3}(?:\.\d{1,3}){3}$/
const IPV6_ADDRESS = /^\[[0-9a-f:.]+\]$/i
const AFTER_HOST = /^[A-Za-z0-9\-._~!$&'()*+,;=:@/%\u0080-\uFFFF]*$/
const INVALID_PERCENT_ESCAPE = /%(?![0-9a-f]{2})/i
const SPACE_OR_CONTROL = /[\p{Z}\p{Cc}]/u

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

export const normalizeHttpBaseUrl = (value: string) => value.trim().replace(/\/+$/, '')

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
])

const HttpPageNumberPaginationApiResponseSchema = z.object({
  type: z.literal(HTTP_PAGINATION_TYPES.PAGE_NUMBER),
  pageParameter: z.string().nullable().optional(),
  pageSizeParameter: z.string().nullable().optional(),
  pageSize: z.number().nullable().optional(),
  startPage: z.number().nullable().optional(),
  endPage: z.number().nullable().optional(),
})

const HttpCursorPaginationApiResponseSchema = z.object({
  type: z.literal(HTTP_PAGINATION_TYPES.CURSOR),
  cursorPath: z.string().nullable().optional(),
  cursorParameter: z.string().nullable().optional(),
  cursorIn: z.enum(HTTP_PARAMETER_LOCATIONS).nullable().optional(),
  limitParameter: z.string().nullable().optional(),
  limit: z.number().nullable().optional(),
})

const HttpPaginationApiResponseSchema = z
  .discriminatedUnion('type', [HttpPageNumberPaginationApiResponseSchema, HttpCursorPaginationApiResponseSchema])
  .transform(pagination => {
    switch (pagination.type) {
      case HTTP_PAGINATION_TYPES.PAGE_NUMBER: {
        const { type, pageParameter, pageSizeParameter, pageSize, startPage, endPage } = pagination
        return {
          isEnabled: true,
          type,
          pageParameter: pageParameter ?? '',
          pageSizeParameter: pageSizeParameter ?? '',
          pageSize: pageSize == null ? '' : String(pageSize),
          startPage: startPage == null ? DEFAULT_START_PAGE : String(startPage),
          endPage: endPage == null ? '' : String(endPage),
        }
      }
      case HTTP_PAGINATION_TYPES.CURSOR: {
        const { type, cursorPath, cursorParameter, cursorIn, limitParameter, limit } = pagination
        return {
          isEnabled: true,
          type,
          cursorPath: cursorPath ?? '',
          cursorParameter: cursorParameter ?? '',
          cursorIn: cursorIn ?? HTTP_PARAMETER_LOCATIONS.QUERY,
          limitParameter: limitParameter ?? '',
          limit: limit == null ? '' : String(limit),
        }
      }
    }
  })

const HttpParameterApiResponseSchema = z
  .object({ name: z.string().nullable().optional(), value: z.string().nullable().optional() })
  .transform(({ name, value }) => ({ name: name ?? '', value: value ?? '' }))

const HttpApiCallApiResponseSchema = z
  .object({
    name: z.string().nullable().optional(),
    method: z.enum(HTTP_METHODS).nullable().optional().catch(undefined),
    path: z.string().nullable().optional(),
    queryParameters: z.array(HttpParameterApiResponseSchema).nullable().optional(),
    headers: z.array(HttpParameterApiResponseSchema).nullable().optional(),
    pagination: HttpPaginationApiResponseSchema.nullable().optional(),
  })
  .transform(({ name, method, path, queryParameters, headers, pagination }) => ({
    name: name ?? '',
    method: method ?? undefined,
    path: path ?? '',
    queryParameters: queryParameters ?? [],
    headers: headers ?? [],
    pagination: pagination ?? EMPTY_PAGINATION,
  }))

export const HttpApiResponseSchema = z.object({
  baseUrl: z.string().nullable().optional(),
  authentication: HttpAuthenticationApiResponseSchema.nullable().optional(),
  apiCalls: z.array(HttpApiCallApiResponseSchema).nullable().optional(),
})

export const HttpApiToFormSchema = HttpApiResponseSchema.transform(({ baseUrl, authentication, apiCalls }) => {
  const isBasic = authentication?.type === HTTP_AUTHENTICATION_TYPES.BASIC
  return {
    baseUrl: baseUrl ?? undefined,
    authentication: {
      type: authentication?.type ?? HTTP_AUTHENTICATION_TYPES.NONE,
      username: isBasic ? (authentication.username ?? '') : '',
      password: isBasic ? (authentication.password ?? '') : '',
    },
    apiCalls: apiCalls ?? [],
  }
})

const HttpAuthenticationFormSchema = z.object({
  type: z.enum(HTTP_AUTHENTICATION_TYPES),
  username: z.string().trim().optional(),
  password: z.string().optional(),
})

type HttpAuthenticationForm = z.infer<typeof HttpAuthenticationFormSchema>

const validateBasicUsername = (authentication: HttpAuthenticationForm, ctx: z.RefinementCtx) => {
  if (authentication.type !== HTTP_AUTHENTICATION_TYPES.BASIC) return
  if (authentication.username?.includes(':')) {
    ctx.addIssue({ code: 'custom', path: ['username'], message: 'datasources.errors.usernameColon' })
  }
}

const requireBasicCredentials = (authentication: HttpAuthenticationForm, ctx: z.RefinementCtx) => {
  if (authentication.type !== HTTP_AUTHENTICATION_TYPES.BASIC) return
  validateBasicUsername(authentication, ctx)
  if (!authentication.username) {
    ctx.addIssue({ code: 'custom', path: ['username'], message: 'common.errors.required' })
  }
  if (!authentication.password) {
    ctx.addIssue({ code: 'custom', path: ['password'], message: 'common.errors.required' })
  }
}

// Same rules as the backend.
const HTTP_PATH = /^\/[A-Za-z0-9._~:/-]*$/
const HTTP_HEADER_NAME = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/
const CREDENTIAL_HEADERS = new Set(['authorization', 'proxy-authorization', 'cookie'])
const CLIENT_HEADERS = new Set(['connection', 'content-length', 'expect', 'host', 'upgrade'])
const JSON_PATH = /^\$(\.[A-Za-z_][A-Za-z0-9_-]*|\['[^'\\]+'\]|\[[0-9]+\])+$/
const LINE_BREAK = /[\r\n]/
const NON_NEGATIVE_INTEGER = /^\d+$/
const MAX_PAGE_NUMBER = 2147483647

const HttpPathSchema = z
  .string()
  .trim()
  .refine(v => !v || HTTP_PATH.test(v), 'datasources.errors.invalidPath')

const HttpParameterFormSchema = z.object({ name: z.string().trim(), value: z.string() })

export type HttpParameterForm = z.infer<typeof HttpParameterFormSchema>

const isEmptyParameter = (parameter: HttpParameterForm) => !parameter.name && !parameter.value

const withoutEmptyParameters = (parameters: HttpParameterForm[]) =>
  parameters.filter(parameter => !isEmptyParameter(parameter))

const removeEmptyParameters = <T extends { queryParameters: HttpParameterForm[]; headers: HttpParameterForm[] }>(
  apiCall: T,
) => ({
  ...apiCall,
  queryParameters: withoutEmptyParameters(apiCall.queryParameters),
  headers: withoutEmptyParameters(apiCall.headers),
})

const requireParameterName = (parameter: HttpParameterForm, ctx: z.RefinementCtx) => {
  if (!parameter.name && parameter.value) {
    ctx.addIssue({ code: 'custom', path: ['name'], message: 'common.errors.required' })
  }
}

const getHeaderNameError = (name: string) => {
  if (!HTTP_HEADER_NAME.test(name)) return 'datasources.errors.invalidHeaderName'
  const lowerCaseName = name.toLowerCase()
  if (CREDENTIAL_HEADERS.has(lowerCaseName)) return 'datasources.errors.credentialHeader'
  if (CLIENT_HEADERS.has(lowerCaseName)) return 'datasources.errors.clientHeader'
  return undefined
}

const validateHeader = (header: HttpParameterForm, ctx: z.RefinementCtx) => {
  const nameError = header.name ? getHeaderNameError(header.name) : undefined
  if (nameError) {
    ctx.addIssue({ code: 'custom', path: ['name'], message: nameError })
  }
  if (LINE_BREAK.test(header.value)) {
    ctx.addIssue({ code: 'custom', path: ['value'], message: 'datasources.errors.headerValueLineBreak' })
  }
}

const HttpPageNumberPaginationFormSchema = z.object({
  isEnabled: z.boolean(),
  type: z.literal(HTTP_PAGINATION_TYPES.PAGE_NUMBER),
  pageParameter: z.string().trim(),
  pageSizeParameter: z.string().trim(),
  pageSize: z.string().trim(),
  startPage: z.string().trim(),
  endPage: z.string().trim(),
})

export type HttpPageNumberPaginationForm = z.input<typeof HttpPageNumberPaginationFormSchema>

const HttpCursorPaginationFormSchema = z.object({
  isEnabled: z.boolean(),
  type: z.literal(HTTP_PAGINATION_TYPES.CURSOR),
  cursorPath: z.string().trim(),
  cursorParameter: z.string().trim(),
  cursorIn: z.enum(HTTP_PARAMETER_LOCATIONS),
  limitParameter: z.string().trim(),
  limit: z.string().trim(),
})

export type HttpCursorPaginationForm = z.input<typeof HttpCursorPaginationFormSchema>

const HttpPaginationFormSchema = z.discriminatedUnion('type', [
  HttpPageNumberPaginationFormSchema,
  HttpCursorPaginationFormSchema,
])

export type HttpPaginationForm = z.input<typeof HttpPaginationFormSchema>

export type HttpPaginationType = (typeof HTTP_PAGINATION_TYPES)[keyof typeof HTTP_PAGINATION_TYPES]

export type HttpParameterLocation = (typeof HTTP_PARAMETER_LOCATIONS)[keyof typeof HTTP_PARAMETER_LOCATIONS]

export const EMPTY_PAGINATION: HttpPaginationForm = {
  isEnabled: false,
  type: HTTP_PAGINATION_TYPES.PAGE_NUMBER,
  pageParameter: '',
  pageSizeParameter: '',
  pageSize: '',
  startPage: DEFAULT_START_PAGE,
  endPage: '',
}

export const EMPTY_CURSOR_PAGINATION: HttpCursorPaginationForm = {
  isEnabled: false,
  type: HTTP_PAGINATION_TYPES.CURSOR,
  cursorPath: '',
  cursorParameter: '',
  cursorIn: HTTP_PARAMETER_LOCATIONS.QUERY,
  limitParameter: '',
  limit: '',
}

export const EMPTY_PAGINATIONS: Record<HttpPaginationType, HttpPaginationForm> = {
  [HTTP_PAGINATION_TYPES.PAGE_NUMBER]: EMPTY_PAGINATION,
  [HTTP_PAGINATION_TYPES.CURSOR]: EMPTY_CURSOR_PAGINATION,
}

const PAGE_NUMBER_FIELDS = ['pageSize', 'startPage', 'endPage'] as const

const getPageNumberError = (value: string) => {
  if (!NON_NEGATIVE_INTEGER.test(value)) return 'datasources.errors.pageNumberInteger'
  if (Number(value) > MAX_PAGE_NUMBER) return 'datasources.errors.pageNumberMax'
  return undefined
}

const validateJsonPath = (value: string, field: string, ctx: z.RefinementCtx) => {
  if (value && !JSON_PATH.test(value)) {
    ctx.addIssue({ code: 'custom', path: [field], message: 'datasources.errors.invalidJsonPath' })
  }
}

const validateLimit = (pagination: { limit: string }, ctx: z.RefinementCtx) => {
  const { limit } = pagination
  if (!limit) return
  if (!NON_NEGATIVE_INTEGER.test(limit) || Number(limit) === 0) {
    ctx.addIssue({ code: 'custom', path: ['limit'], message: 'datasources.errors.limitInteger' })
  } else if (Number(limit) > MAX_PAGE_NUMBER) {
    ctx.addIssue({ code: 'custom', path: ['limit'], message: 'datasources.errors.pageNumberMax' })
  }
}

const requireLimitPair = (pagination: { limitParameter: string; limit: string }, ctx: z.RefinementCtx) => {
  if (pagination.limitParameter && !pagination.limit) {
    ctx.addIssue({ code: 'custom', path: ['limit'], message: 'datasources.errors.limitRequired' })
  }
  if (pagination.limit && !pagination.limitParameter) {
    ctx.addIssue({ code: 'custom', path: ['limitParameter'], message: 'datasources.errors.limitParameterRequired' })
  }
}

const validatePageNumberPagination = (pagination: HttpPageNumberPaginationForm, ctx: z.RefinementCtx) => {
  const invalidFields = PAGE_NUMBER_FIELDS.filter(field => pagination[field] && getPageNumberError(pagination[field]))
  for (const field of invalidFields) {
    ctx.addIssue({ code: 'custom', path: [field], message: getPageNumberError(pagination[field]) })
  }
  if (!invalidFields.includes('pageSize') && pagination.pageSize && Number(pagination.pageSize) === 0) {
    ctx.addIssue({ code: 'custom', path: ['pageSize'], message: 'datasources.errors.pageSizeMin' })
  }
  const canCompare = !invalidFields.includes('startPage') && !invalidFields.includes('endPage')
  if (canCompare && pagination.endPage && Number(pagination.endPage) < Number(pagination.startPage)) {
    ctx.addIssue({ code: 'custom', path: ['endPage'], message: 'datasources.errors.endPageBeforeStartPage' })
  }
}

const validateCursorPagination = (pagination: HttpCursorPaginationForm, ctx: z.RefinementCtx) => {
  validateJsonPath(pagination.cursorPath, 'cursorPath', ctx)
  validateLimit(pagination, ctx)
}

const validatePagination = (pagination: HttpPaginationForm, ctx: z.RefinementCtx) => {
  if (!pagination.isEnabled) return
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
      validatePageNumberPagination(pagination, ctx)
      break
    case HTTP_PAGINATION_TYPES.CURSOR:
      validateCursorPagination(pagination, ctx)
      break
  }
}

const REQUIRED_PAGE_NUMBER_FIELDS = ['pageParameter', 'pageSizeParameter', 'pageSize'] as const
const REQUIRED_CURSOR_FIELDS = ['cursorPath', 'cursorParameter'] as const

const requireFields = <K extends string>(pagination: Record<K, string>, fields: readonly K[], ctx: z.RefinementCtx) => {
  for (const field of fields) {
    if (!pagination[field]) {
      ctx.addIssue({ code: 'custom', path: [field], message: 'common.errors.required' })
    }
  }
}

const requirePagination = (pagination: HttpPaginationForm, ctx: z.RefinementCtx) => {
  if (!pagination.isEnabled) return
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
      requireFields(pagination, REQUIRED_PAGE_NUMBER_FIELDS, ctx)
      break
    case HTTP_PAGINATION_TYPES.CURSOR:
      requireFields(pagination, REQUIRED_CURSOR_FIELDS, ctx)
      requireLimitPair(pagination, ctx)
      break
  }
}

const toPaginationApi = (pagination: HttpPaginationForm) => {
  if (!pagination.isEnabled) return null
  const toNumber = (value: string) => (value ? Number(value) : null)
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
      return {
        type: pagination.type,
        pageParameter: pagination.pageParameter || null,
        pageSizeParameter: pagination.pageSizeParameter || null,
        pageSize: toNumber(pagination.pageSize),
        startPage: toNumber(pagination.startPage),
        endPage: toNumber(pagination.endPage),
      }
    case HTTP_PAGINATION_TYPES.CURSOR:
      return {
        type: pagination.type,
        cursorPath: pagination.cursorPath || null,
        cursorParameter: pagination.cursorParameter || null,
        cursorIn: pagination.cursorIn,
        limitParameter: pagination.limitParameter || null,
        limit: toNumber(pagination.limit),
      }
  }
}

type PaginationParameter = { field: string; name: string; location: HttpParameterLocation }

const getPaginationParameters = (pagination: HttpPaginationForm): PaginationParameter[] => {
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER: {
      const paginationParameters: PaginationParameter[] = [
        { field: 'pageParameter', name: pagination.pageParameter, location: HTTP_PARAMETER_LOCATIONS.QUERY },
        { field: 'pageSizeParameter', name: pagination.pageSizeParameter, location: HTTP_PARAMETER_LOCATIONS.QUERY },
      ]
      return paginationParameters.filter(parameter => parameter.name)
    }
    case HTTP_PAGINATION_TYPES.CURSOR: {
      const paginationParameters: PaginationParameter[] = [
        { field: 'cursorParameter', name: pagination.cursorParameter, location: pagination.cursorIn },
        { field: 'limitParameter', name: pagination.limitParameter, location: HTTP_PARAMETER_LOCATIONS.QUERY },
      ]
      return paginationParameters.filter(parameter => parameter.name)
    }
  }
}

type RequestParameter = Pick<PaginationParameter, 'name' | 'location'>

const isSameName = (a: RequestParameter, b: RequestParameter) => {
  if (a.location !== b.location) return false
  if (a.location === HTTP_PARAMETER_LOCATIONS.HEADER) return a.name.toLowerCase() === b.name.toLowerCase()
  return a.name === b.name
}

const getParameterIndexes = (
  parameters: HttpParameterForm[],
  location: HttpParameterLocation,
  paginationParameter: RequestParameter,
) =>
  parameters.flatMap((parameter, index) =>
    isSameName({ name: parameter.name, location }, paginationParameter) ? [index] : [],
  )

const PARAMETER_LISTS = [
  {
    list: 'queryParameters',
    location: HTTP_PARAMETER_LOCATIONS.QUERY,
    message: 'datasources.errors.paginationQueryDuplicate',
  },
  {
    list: 'headers',
    location: HTTP_PARAMETER_LOCATIONS.HEADER,
    message: 'datasources.errors.paginationHeaderDuplicate',
  },
] as const

const validatePaginationNames = (apiCall: HttpApiCallBaseFormOutput, ctx: z.RefinementCtx) => {
  if (!apiCall.pagination.isEnabled) return
  const paginationParameters = getPaginationParameters(apiCall.pagination)
  for (const [index, paginationParameter] of paginationParameters.entries()) {
    const addIssue = (message: string) =>
      ctx.addIssue({ code: 'custom', path: ['pagination', paginationParameter.field], message })
    const headerNameError =
      paginationParameter.location === HTTP_PARAMETER_LOCATIONS.HEADER
        ? getHeaderNameError(paginationParameter.name)
        : undefined
    if (headerNameError) {
      addIssue(headerNameError)
    }
    for (const { list, location, message } of PARAMETER_LISTS) {
      const parameterIndexes = getParameterIndexes(apiCall[list], location, paginationParameter)
      if (parameterIndexes.length) {
        addIssue(message)
      }
      for (const parameterIndex of parameterIndexes) {
        ctx.addIssue({
          code: 'custom',
          path: [list, parameterIndex, 'name'],
          message: 'datasources.errors.parameterPaginationDuplicate',
        })
      }
    }
    const earlierParameters = paginationParameters.slice(0, index)
    const earlierParameter = earlierParameters.find(earlier => isSameName(earlier, paginationParameter))
    if (earlierParameter) {
      addIssue('datasources.errors.paginationParameterDuplicate')
      ctx.addIssue({
        code: 'custom',
        path: ['pagination', earlierParameter.field],
        message: 'datasources.errors.paginationParameterDuplicate',
      })
    }
  }
}

const HttpApiCallBaseFormSchema = z.object({
  name: z.string().trim(),
  path: HttpPathSchema,
  queryParameters: z.array(HttpParameterFormSchema),
  headers: z.array(HttpParameterFormSchema.superRefine(validateHeader)),
  pagination: HttpPaginationFormSchema.superRefine(validatePagination),
})

type HttpApiCallBaseFormOutput = z.output<typeof HttpApiCallBaseFormSchema>

const HttpApiCallFormSchema =
  HttpApiCallBaseFormSchema.superRefine(validatePaginationNames).transform(removeEmptyParameters)

export type HttpApiCallForm = z.input<typeof HttpApiCallFormSchema>

export type HttpApiCallsForm = { configuration: { baseUrl: string; apiCalls: HttpApiCallForm[] } }

// Key: index of the API call. Value: its values when the edit view opened, null for a new API call.
export type ApiCallSnapshots = ReadonlyMap<number, HttpApiCallForm | null>

export type ApiCallSection = (typeof API_CALL_SECTIONS)[number]

// Key: index of the API call. Value: its collapsed sections.
export type CollapsedApiCallSections = ReadonlyMap<number, ReadonlySet<ApiCallSection>>

export type HttpParameterListPath =
  `configuration.apiCalls.${number}.queryParameters` | `configuration.apiCalls.${number}.headers`

export const getParameterNameCollisionPaths = (index: number) =>
  [
    `configuration.apiCalls.${index}.pagination`,
    `configuration.apiCalls.${index}.queryParameters`,
    `configuration.apiCalls.${index}.headers`,
  ] as const

const HttpApiCallStrictFormSchema = HttpApiCallBaseFormSchema.extend({
  name: z.string().trim().min(1, 'common.errors.required'),
  path: z.string().trim().min(1, 'common.errors.required').pipe(HttpPathSchema),
  queryParameters: z.array(HttpParameterFormSchema.superRefine(requireParameterName)),
  headers: z.array(HttpParameterFormSchema.superRefine(validateHeader).superRefine(requireParameterName)),
  pagination: HttpPaginationFormSchema.superRefine(validatePagination).superRefine(requirePagination),
})
  .superRefine(validatePaginationNames)
  .transform(removeEmptyParameters)

const HttpApiCallsLooseSchema = z.array(HttpApiCallFormSchema).max(MAX_HTTP_API_CALLS, 'datasources.errors.apiCallsMax')

export const HttpApiCallsStrictSchema = z
  .array(HttpApiCallStrictFormSchema)
  .min(1, 'common.errors.required')
  .max(MAX_HTTP_API_CALLS, 'datasources.errors.apiCallsMax')

export const HttpLooseSchema = z.object({
  baseUrl: HttpBaseUrlSchema.optional(),
  authentication: HttpAuthenticationFormSchema.superRefine(validateBasicUsername).optional(),
  apiCalls: HttpApiCallsLooseSchema.optional(),
})

export const HttpStrictSchema = z.object({
  baseUrl: z.string().trim().min(1, 'common.errors.required').pipe(HttpBaseUrlSchema),
  authentication: HttpAuthenticationFormSchema.superRefine(requireBasicCredentials),
  apiCalls: HttpApiCallsStrictSchema,
})

const HttpFormToApiSchema = HttpLooseSchema.transform(({ authentication, apiCalls, ...rest }) => {
  const apiCallsApi = apiCalls?.map(apiCall => ({ ...apiCall, pagination: toPaginationApi(apiCall.pagination) }))
  if (authentication?.type === HTTP_AUTHENTICATION_TYPES.BASIC) {
    return { ...rest, apiCalls: apiCallsApi, authentication }
  }
  return { ...rest, apiCalls: apiCallsApi, authentication: { type: HTTP_AUTHENTICATION_TYPES.NONE } }
})

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
  HTTP: HttpStrictSchema.omit({ apiCalls: true }),
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
