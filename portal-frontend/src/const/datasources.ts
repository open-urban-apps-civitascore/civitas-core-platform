/* eslint-disable @typescript-eslint/naming-convention */
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
  MQTT: 'mqtt',
  SQL: 'sql',
} as const

export const CONNECTOR_TYPE_KEYS: Record<ConnectorType, ConnectorTypeKey> = Object.fromEntries(
  Object.entries(CONNECTOR_TYPES).map(([k, v]) => [v, k]),
) as Record<ConnectorType, ConnectorTypeKey>

export const CONNECTOR_DEFAULTS = {
  mqtt: {
    urls: [],
    topics: [],
    client_id: 'abc',
    qos: '1',
    connect_timeout: '',
    keepalive: '',
    tls: false,
  },
  sql: {
    driver: 'postgres',
    dsn: '',
    table: '',
    columns: ['*'],
    where: '',
    args_mapping: [],
    prefix: '',
    suffix: '',
    init_files: [],
    init_statement: '',
    conn_max_idle_time: '',
    conn_max_life_time: '',
    conn_max_idle: 2,
    conn_max_open: 0,
  },
}
