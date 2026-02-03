import { z } from 'zod'

import { CONNECTOR_DEFAULTS, CONNECTOR_TYPES, DATASOURCE_STATUS_TYPES } from '@/const/datasources'

export type ConnectorTypeKey = keyof typeof CONNECTOR_TYPES

export type ConnectorType = (typeof CONNECTOR_TYPES)[keyof typeof CONNECTOR_TYPES]

export type DatasourceStatusType = (typeof DATASOURCE_STATUS_TYPES)[keyof typeof DATASOURCE_STATUS_TYPES]

const parseStringArray = (v: unknown): string[] | undefined => {
  if (v === undefined) return undefined
  if (Array.isArray(v)) return v.map(String)
  if (typeof v === 'string')
    return v
      .split(',')
      .map(s => s.trim())
      .filter(Boolean)
  return undefined
}

export const MqttSchema = z.object({
  urls: z.preprocess(parseStringArray, z.array(z.string()).min(1)).optional(),
  topics: z.preprocess(parseStringArray, z.array(z.string()).min(1)).optional(),
  clientId: z.string().optional(),
  qos: z.enum(['0', '1', '2']).optional(),
  connectTimeout: z.string().optional(),
  keepalive: z.string().optional(),
  tls: z.boolean().optional(),
})

export const SqlSchema = z.object({
  driver: z
    .enum(['postgres', 'mysql', 'clickhouse', 'mssql', 'sqlite', 'oracle', 'snowflake', 'trino', 'gocosmos', 'spanner'])
    .optional(),
  dsn: z.string().min(1).optional(),
  table: z.string().min(1).optional(),
  columns: z.preprocess(parseStringArray, z.array(z.string()).min(1)).optional(),
  where: z.string().optional(),
  argsMapping: z.array(z.string()).optional(),
  prefix: z.string().optional(),
  suffix: z.string().optional(),
  initFiles: z.array(z.string()).optional(),
  initStatement: z.string().optional(),
  connMaxIdleTime: z.string().optional(),
  connMaxLifeTime: z.string().optional(),
  connMaxIdle: z.number().int().nonnegative().optional(),
  connMaxOpen: z.number().int().nonnegative().optional(),
})

export type ConnectorConfig = {
  mqtt: z.infer<typeof MqttSchema>
  sql: z.infer<typeof SqlSchema>
}

export const ConnectorSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal(CONNECTOR_TYPES.MQTT),
    config: MqttSchema.optional(),
  }),
  z.object({
    type: z.literal(CONNECTOR_TYPES.SQL),
    config: SqlSchema.optional(),
  }),
])

export type ConnectorInput = z.input<typeof ConnectorSchema>
export type ConnectorOutput = z.output<typeof ConnectorSchema>

export const getConnectorDefaults = <T extends ConnectorType>(
  type: T,
): T extends 'mqtt' ? z.input<typeof MqttSchema> : z.input<typeof SqlSchema> => {
  return CONNECTOR_DEFAULTS[type] as never
}
