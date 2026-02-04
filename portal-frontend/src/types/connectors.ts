/* eslint-disable @typescript-eslint/naming-convention */
import { z } from 'zod'

import { CONNECTOR_TYPES } from '@/const/connectors'

export type ConnectorTypeKey = keyof typeof CONNECTOR_TYPES

export type ConnectorType = (typeof CONNECTOR_TYPES)[keyof typeof CONNECTOR_TYPES]

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

const MqttBaseSchema = z.object({
  urls: z.string().optional(),
  topics: z.string().optional(),
  clientId: z.string().optional(),
  qos: z.enum(['0', '1', '2']).optional(),
  connect_timeout: z.string().optional(),
  keepalive: z.string().optional(),
  tls: z.boolean().optional(),
})

export const MqttLooseSchema = MqttBaseSchema

export const MqttStrictSchema = MqttBaseSchema.extend({
  urls: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
  topics: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
})
export const MqttApiSchema = MqttBaseSchema.extend({
  urls: z.preprocess(parseStringArray, z.array(z.string())),
  topics: z.preprocess(parseStringArray, z.array(z.string())),
})

const SqlBaseSchema = z.object({
  driver: z
    .enum(['postgres', 'mysql', 'clickhouse', 'mssql', 'sqlite', 'oracle', 'snowflake', 'trino', 'gocosmos', 'spanner'])
    .optional(),
  dsn: z.string().optional(),
  table: z.string().optional(),
  columns: z.string().optional(),
  where: z.string().optional(),
  args_mapping: z.string().optional(),
  prefix: z.string().optional(),
  suffix: z.string().optional(),
  init_files: z.string().optional(),
  init_statement: z.string().optional(),
  conn_max_idle_time: z.string().optional(),
  conn_max_life_time: z.string().optional(),
  conn_max_idle: z.number().int().nonnegative().optional(),
  conn_max_open: z.number().int().nonnegative().optional(),
})

export const SqlLooseSchema = SqlBaseSchema

export const SqlStrictSchema = SqlBaseSchema.extend({
  dsn: z.string().min(1, 'required'),
  table: z.string().min(1, 'required'),
  columns: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
  init_files: z.preprocess(parseStringArray, z.array(z.string())),
})
export const SqlApiSchema = SqlBaseSchema.extend({
  columns: z.preprocess(parseStringArray, z.array(z.string())),
  init_files: z.preprocess(parseStringArray, z.array(z.string())),
})

export type MqttLooseConfig = z.infer<typeof MqttLooseSchema>
export type MqttStrictConfig = z.infer<typeof MqttStrictSchema>
export type MqttApiConfig = z.infer<typeof MqttApiSchema>
export type SqlLooseConfig = z.infer<typeof SqlLooseSchema>
export type SqlStrictConfig = z.infer<typeof SqlStrictSchema>
export type SqlApiConfig = z.infer<typeof SqlApiSchema>

export const ConnectorLooseSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal(CONNECTOR_TYPES.MQTT),
    config: MqttLooseSchema,
  }),
  z.object({
    type: z.literal(CONNECTOR_TYPES.SQL),
    config: SqlLooseSchema,
  }),
])

export const ConnectorStrictSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal(CONNECTOR_TYPES.MQTT),
    config: MqttStrictSchema,
  }),
  z.object({
    type: z.literal(CONNECTOR_TYPES.SQL),
    config: SqlStrictSchema,
  }),
])

export const ConnectorApiSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal(CONNECTOR_TYPES.MQTT),
    config: MqttApiSchema,
  }),
  z.object({
    type: z.literal(CONNECTOR_TYPES.SQL),
    config: SqlApiSchema,
  }),
])

export type ConnectorApiConfig = {
  mqtt: z.infer<typeof MqttApiSchema>
  sql: z.infer<typeof SqlApiSchema>
}

export type ConnectorDraft = z.input<typeof ConnectorLooseSchema>
export type ConnectorAvailable = z.output<typeof ConnectorStrictSchema>
export type ConnectorApiData = z.output<typeof ConnectorApiSchema>
