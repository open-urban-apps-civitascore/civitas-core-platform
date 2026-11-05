import type { ConnectorType, ConnectorTypeKey } from '@/types/connectors'
import type {
  HttpCursorPaginationForm,
  HttpLimitOffsetPaginationForm,
  HttpPaginationForm,
  HttpPaginationType,
} from '@/types/httpApiCalls'

export const CONNECTION_TYPES = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  STATIC: 'static',
} as const

export const CONNECTOR_TYPES = {
  MQTT: 'MQTT',
  SQL: 'SQL',
  HTTP: 'HTTP',
} as const

export const HTTP_AUTHENTICATION_TYPES = {
  NONE: 'NONE',
  BASIC: 'BASIC',
  BEARER_TOKEN: 'BEARER_TOKEN',
  API_TOKEN: 'API_TOKEN',
  OAUTH2: 'OAUTH2',
  CLIENT_CERTIFICATE: 'CLIENT_CERTIFICATE',
} as const

export const HTTP_METHODS = {
  GET: 'GET',
} as const

export const HTTP_PAGINATION_TYPES = {
  PAGE_NUMBER: 'PAGE_NUMBER',
  LIMIT_OFFSET: 'LIMIT_OFFSET',
  CURSOR: 'CURSOR',
} as const

export const HTTP_PARAMETER_LOCATIONS = {
  QUERY: 'QUERY',
  HEADER: 'HEADER',
} as const

export const DEFAULT_START_PAGE = '0'

export const DEFAULT_OFFSET = '0'

export const MAX_HTTP_API_CALLS = 1

export const MAX_PAGE_NUMBER = 2147483647

export const SECRET_PLACEHOLDER = '********'

export const EMPTY_PAGINATION: HttpPaginationForm = {
  isEnabled: false,
  type: HTTP_PAGINATION_TYPES.PAGE_NUMBER,
  pageParameter: '',
  pageSizeParameter: '',
  pageSize: '',
  startPage: DEFAULT_START_PAGE,
  endPage: '',
}

export const EMPTY_LIMIT_OFFSET_PAGINATION: HttpLimitOffsetPaginationForm = {
  isEnabled: false,
  type: HTTP_PAGINATION_TYPES.LIMIT_OFFSET,
  offsetParameter: '',
  offset: DEFAULT_OFFSET,
  limitParameter: '',
  limit: '',
  countPath: '',
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
  [HTTP_PAGINATION_TYPES.LIMIT_OFFSET]: EMPTY_LIMIT_OFFSET_PAGINATION,
  [HTTP_PAGINATION_TYPES.CURSOR]: EMPTY_CURSOR_PAGINATION,
}

export const API_CALL_SECTIONS = ['request', 'pagination'] as const

export const CONNECTOR_TYPE_KEYS: Record<ConnectorType, ConnectorTypeKey> = Object.fromEntries(
  Object.entries(CONNECTOR_TYPES).map(([k, v]) => [v, k]),
) as Record<ConnectorType, ConnectorTypeKey>
