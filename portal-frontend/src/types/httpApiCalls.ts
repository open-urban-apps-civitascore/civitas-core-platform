import { z } from 'zod'

import {
  API_CALL_SECTIONS,
  DEFAULT_OFFSET,
  DEFAULT_START_PAGE,
  EMPTY_PAGINATION,
  HTTP_METHODS,
  HTTP_PAGINATION_TYPES,
  HTTP_PARAMETER_LOCATIONS,
  MAX_HTTP_API_CALLS,
  MAX_PAGE_NUMBER,
} from '@/const/connectors'
import type { HttpAuthenticationForm } from '@/types/connectors'

// Same rules as the backend.
const HTTP_PATH = /^\/[A-Za-z0-9._~:/-]*$/
const HTTP_HEADER_NAME = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/
const JSON_PATH = /^\$(\.[A-Za-z_][A-Za-z0-9_-]*|\['[^'\\]+'\]|\[[0-9]+\])+$/
const LINE_BREAK = /[\r\n]/
const NON_NEGATIVE_INTEGER = /^\d+$/

const HttpPageNumberPaginationApiResponseSchema = z.object({
  type: z.literal(HTTP_PAGINATION_TYPES.PAGE_NUMBER),
  pageParameter: z.string().nullable().optional(),
  pageSizeParameter: z.string().nullable().optional(),
  pageSize: z.number().nullable().optional(),
  startPage: z.number().nullable().optional(),
  endPage: z.number().nullable().optional(),
})

const HttpLimitOffsetPaginationApiResponseSchema = z.object({
  type: z.literal(HTTP_PAGINATION_TYPES.LIMIT_OFFSET),
  offsetParameter: z.string().nullable().optional(),
  offset: z.number().nullable().optional(),
  limitParameter: z.string().nullable().optional(),
  limit: z.number().nullable().optional(),
  countPath: z.string().nullable().optional(),
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
  .discriminatedUnion('type', [
    HttpPageNumberPaginationApiResponseSchema,
    HttpLimitOffsetPaginationApiResponseSchema,
    HttpCursorPaginationApiResponseSchema,
  ])
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
      case HTTP_PAGINATION_TYPES.LIMIT_OFFSET: {
        const { type, offsetParameter, offset, limitParameter, limit, countPath } = pagination
        return {
          isEnabled: true,
          type,
          offsetParameter: offsetParameter ?? '',
          offset: offset == null ? DEFAULT_OFFSET : String(offset),
          limitParameter: limitParameter ?? '',
          limit: limit == null ? '' : String(limit),
          countPath: countPath ?? '',
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

export const HttpApiCallApiResponseSchema = z
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
const HttpPathSchema = z
  .string()
  .trim()
  .refine(v => !v || HTTP_PATH.test(v), 'datasources.errors.invalidPath')

const HttpParameterFormSchema = z.object({ name: z.string().trim(), value: z.string() })

export type HttpParameterForm = z.infer<typeof HttpParameterFormSchema>

const requireParameterName = (parameter: HttpParameterForm, ctx: z.RefinementCtx) => {
  if (!parameter.name && parameter.value) {
    ctx.addIssue({ code: 'custom', path: ['name'], message: 'common.errors.required' })
  }
}

const CREDENTIAL_HEADERS = new Set(['authorization', 'proxy-authorization', 'cookie'])
const CLIENT_HEADERS = new Set(['connection', 'content-length', 'expect', 'host', 'upgrade'])

export const getHeaderNameError = (name: string, isCredentialHeaderAllowed = false) => {
  if (!HTTP_HEADER_NAME.test(name)) return 'datasources.errors.invalidHeaderName'
  const lowerCaseName = name.toLowerCase()
  if (!isCredentialHeaderAllowed && CREDENTIAL_HEADERS.has(lowerCaseName)) return 'datasources.errors.credentialHeader'
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

const HttpLimitOffsetPaginationFormSchema = z.object({
  isEnabled: z.boolean(),
  type: z.literal(HTTP_PAGINATION_TYPES.LIMIT_OFFSET),
  offsetParameter: z.string().trim(),
  offset: z.string().trim(),
  limitParameter: z.string().trim(),
  limit: z.string().trim(),
  countPath: z.string().trim(),
})

export type HttpLimitOffsetPaginationForm = z.input<typeof HttpLimitOffsetPaginationFormSchema>

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
  HttpLimitOffsetPaginationFormSchema,
  HttpCursorPaginationFormSchema,
])

export type HttpPaginationForm = z.input<typeof HttpPaginationFormSchema>

export type HttpPaginationType = (typeof HTTP_PAGINATION_TYPES)[keyof typeof HTTP_PAGINATION_TYPES]

export type HttpParameterLocation = (typeof HTTP_PARAMETER_LOCATIONS)[keyof typeof HTTP_PARAMETER_LOCATIONS]

const PAGE_NUMBER_FIELDS = ['pageSize', 'startPage', 'endPage'] as const

const getPageNumberError = (value: string) => {
  if (!NON_NEGATIVE_INTEGER.test(value)) return 'datasources.errors.pageNumberInteger'
  if (Number(value) > MAX_PAGE_NUMBER) return 'datasources.errors.pageNumberMax'
  return undefined
}

const validateJsonPath = (value: string, field: string, message: string, ctx: z.RefinementCtx) => {
  if (value && !JSON_PATH.test(value)) {
    ctx.addIssue({ code: 'custom', path: [field], message })
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

const validateLimitOffsetPagination = (pagination: HttpLimitOffsetPaginationForm, ctx: z.RefinementCtx) => {
  const offsetError = pagination.offset ? getPageNumberError(pagination.offset) : undefined
  if (offsetError) {
    ctx.addIssue({ code: 'custom', path: ['offset'], message: offsetError })
  }
  validateLimit(pagination, ctx)
  validateJsonPath(pagination.countPath, 'countPath', 'datasources.errors.invalidCountPath', ctx)
}

const validateCursorPagination = (pagination: HttpCursorPaginationForm, ctx: z.RefinementCtx) => {
  validateJsonPath(pagination.cursorPath, 'cursorPath', 'datasources.errors.invalidCursorPath', ctx)
  validateLimit(pagination, ctx)
}

const validatePagination = (pagination: HttpPaginationForm, ctx: z.RefinementCtx) => {
  if (!pagination.isEnabled) return
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
      validatePageNumberPagination(pagination, ctx)
      break
    case HTTP_PAGINATION_TYPES.LIMIT_OFFSET:
      validateLimitOffsetPagination(pagination, ctx)
      break
    case HTTP_PAGINATION_TYPES.CURSOR:
      validateCursorPagination(pagination, ctx)
      break
  }
}

const REQUIRED_PAGE_NUMBER_FIELDS = ['pageParameter', 'pageSizeParameter', 'pageSize'] as const
const REQUIRED_LIMIT_OFFSET_FIELDS = ['offsetParameter'] as const
const REQUIRED_CURSOR_FIELDS = ['cursorPath', 'cursorParameter'] as const

export const requireFields = <K extends string>(
  values: Partial<Record<K, string>>,
  fields: readonly K[],
  ctx: z.RefinementCtx,
) => {
  for (const field of fields) {
    if (!values[field]?.trim()) {
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
    case HTTP_PAGINATION_TYPES.LIMIT_OFFSET:
      requireFields(pagination, REQUIRED_LIMIT_OFFSET_FIELDS, ctx)
      requireLimitPair(pagination, ctx)
      break
    case HTTP_PAGINATION_TYPES.CURSOR:
      requireFields(pagination, REQUIRED_CURSOR_FIELDS, ctx)
      requireLimitPair(pagination, ctx)
      break
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
    case HTTP_PAGINATION_TYPES.LIMIT_OFFSET: {
      const paginationParameters: PaginationParameter[] = [
        { field: 'limitParameter', name: pagination.limitParameter, location: HTTP_PARAMETER_LOCATIONS.QUERY },
        { field: 'offsetParameter', name: pagination.offsetParameter, location: HTTP_PARAMETER_LOCATIONS.QUERY },
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

const HttpApiCallFormSchema = HttpApiCallBaseFormSchema.superRefine(validatePaginationNames)

export type HttpApiCallForm = z.input<typeof HttpApiCallFormSchema>

export type HttpApiCallsForm = {
  configuration: { baseUrl: string; authentication?: HttpAuthenticationForm; apiCalls: HttpApiCallForm[] }
}

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
    'configuration.authentication.name',
  ] as const

const HttpApiCallStrictFormSchema = HttpApiCallBaseFormSchema.extend({
  name: z.string().trim().min(1, 'common.errors.required'),
  path: z.string().trim().min(1, 'common.errors.required').pipe(HttpPathSchema),
  queryParameters: z.array(HttpParameterFormSchema.superRefine(requireParameterName)),
  headers: z.array(HttpParameterFormSchema.superRefine(validateHeader).superRefine(requireParameterName)),
  pagination: HttpPaginationFormSchema.superRefine(validatePagination).superRefine(requirePagination),
}).superRefine(validatePaginationNames)

export const HttpApiCallsLooseSchema = z
  .array(HttpApiCallFormSchema)
  .max(MAX_HTTP_API_CALLS, 'datasources.errors.apiCallsMax')

export const HttpApiCallsStrictSchema = z
  .array(HttpApiCallStrictFormSchema)
  .min(1, 'common.errors.required')
  .max(MAX_HTTP_API_CALLS, 'datasources.errors.apiCallsMax')
export const getApiTokenCollisionPaths = (token: RequestParameter, apiCall: z.output<typeof HttpApiCallFormSchema>) => {
  const parameterPaths = PARAMETER_LISTS.flatMap(({ list, location }) =>
    getParameterIndexes(apiCall[list], location, token).map(index => [list, index, 'name']),
  )
  const paginationParameters = apiCall.pagination.isEnabled ? getPaginationParameters(apiCall.pagination) : []
  const collidingPaginationParameters = paginationParameters.filter(parameter => isSameName(token, parameter))
  const paginationPaths = collidingPaginationParameters.map(({ field }) => ['pagination', field])
  return [...parameterPaths, ...paginationPaths]
}
