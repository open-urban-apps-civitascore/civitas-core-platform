import { ConnectorType, ConnectorTypeKey } from '@/types/connectors'

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
} as const

export const HTTP_METHODS = {
  GET: 'GET',
} as const

export const HTTP_PAGINATION_TYPES = {
  PAGE_NUMBER: 'PAGE_NUMBER',
} as const

export const DEFAULT_START_PAGE = '0'

export const MAX_HTTP_API_CALLS = 1

export const API_CALL_SECTIONS = ['request', 'pagination'] as const

export const CONNECTOR_TYPE_KEYS: Record<ConnectorType, ConnectorTypeKey> = Object.fromEntries(
  Object.entries(CONNECTOR_TYPES).map(([k, v]) => [v, k]),
) as Record<ConnectorType, ConnectorTypeKey>
