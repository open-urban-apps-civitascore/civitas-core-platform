import { ConnectorType, ConnectorTypeKey } from '@/types/connectors'

export const CONNECTION_TYPES = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  STATIC: 'static',
} as const

export const DATASOURCE_STATUS_TYPES = {
  DRAFT: 'draft',
  AVAILABLE: 'available',
} as const

export const CONNECTOR_TYPES = {
  MQTT: 'MQTT',
  SQL: 'SQL',
} as const

export const CONNECTOR_TYPE_KEYS: Record<ConnectorType, ConnectorTypeKey> = Object.fromEntries(
  Object.entries(CONNECTOR_TYPES).map(([k, v]) => [v, k]),
) as Record<ConnectorType, ConnectorTypeKey>
