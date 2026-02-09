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

const MqttBaseSchema = z.object({
  urls: z.string().trim().optional(),
  topics: z.string().trim().optional(),
  clientId: z.string().trim().optional(),
  qos: z.enum(['0', '1', '2']).optional(),
  connect_timeout: z.string().trim().optional(),
  keepalive: z.string().trim().optional(),
  tls: z.boolean().optional(),
})

export const MqttLooseSchema = MqttBaseSchema

export const MqttStrictSchema = MqttBaseSchema.extend({
  urls: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
  topics: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
})

export const MqttApiResponseSchema = MqttBaseSchema.extend({
  urls: z.array(z.string()),
  topics: z.array(z.string()),
})

export const MqttFormToApiSchema = MqttBaseSchema.extend({
  urls: z.preprocess(parseStringArray, z.array(z.string())),
  topics: z.preprocess(parseStringArray, z.array(z.string())),
})

export const MqttApiToFormSchema = MqttBaseSchema.extend({
  urls: z.preprocess(stringifyStringArray, z.string()),
  topics: z.preprocess(stringifyStringArray, z.string()),
})

const SqlBaseSchema = z.object({
  driver: z
    .enum(['postgres', 'mysql', 'clickhouse', 'mssql', 'sqlite', 'oracle', 'snowflake', 'trino', 'gocosmos', 'spanner'])
    .optional(),
  dsn: z.string().trim().optional(),
  table: z.string().trim().optional(),
  columns: z.string().trim().optional(),
  where: z.string().trim().optional(),
  args_mapping: z.string().trim().optional(),
  prefix: z.string().trim().optional(),
  suffix: z.string().trim().optional(),
  init_files: z.string().trim().optional(),
  init_statement: z.string().trim().optional(),
  conn_max_idle_time: z.string().trim().optional(),
  conn_max_life_time: z.string().trim().optional(),
  conn_max_idle: z.number().int().nonnegative().optional(),
  conn_max_open: z.number().int().nonnegative().optional(),
})

export const SqlLooseSchema = SqlBaseSchema

export const SqlStrictSchema = SqlBaseSchema.extend({
  dsn: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  table: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  columns: z.preprocess(parseStringArray, z.array(z.string()).min(1, 'required')),
  init_files: z.preprocess(parseStringArray, z.array(z.string())),
})

export const SqlApiResponseSchema = SqlBaseSchema.extend({
  columns: z.array(z.string()),
  init_files: z.array(z.string()),
})

export const SqlFormToApiSchema = SqlBaseSchema.extend({
  dsn: z.string().trim(),
  table: z.string().trim(),
  columns: z.preprocess(parseStringArray, z.array(z.string())),
  init_files: z.preprocess(parseStringArray, z.array(z.string())),
})

export const SqlApiToFormSchema = SqlBaseSchema.extend({
  columns: z.preprocess(stringifyStringArray, z.string()),
  init_files: z.preprocess(stringifyStringArray, z.string()),
})

const ConnectorApiResponseConfig = {
  [CONNECTOR_TYPES.mqtt]: MqttApiResponseSchema,
  [CONNECTOR_TYPES.sql]: SqlApiResponseSchema,
} as const

const ConnectorLooseConfig = {
  [CONNECTOR_TYPES.mqtt]: MqttLooseSchema,
  [CONNECTOR_TYPES.sql]: SqlLooseSchema,
} as const

const ConnectorStrictConfig = {
  [CONNECTOR_TYPES.mqtt]: MqttStrictSchema,
  [CONNECTOR_TYPES.sql]: SqlStrictSchema,
} as const

const ConnectorApiToFormConfig = {
  [CONNECTOR_TYPES.mqtt]: MqttApiToFormSchema,
  [CONNECTOR_TYPES.sql]: SqlApiToFormSchema,
} as const

const ConnectorFormToApiConfig = {
  [CONNECTOR_TYPES.mqtt]: MqttFormToApiSchema,
  [CONNECTOR_TYPES.sql]: SqlFormToApiSchema,
} as const

export const ConnectorApiResponseSchema = z.union(
  Object.entries(ConnectorApiResponseConfig).map(([type, schema]) =>
    z.object({
      type: z.literal(type as ConnectorType),
      config: schema,
    }),
  ),
)

export const ConnectorLooseSchema = z.union(
  Object.entries(ConnectorLooseConfig).map(([type, schema]) =>
    z.object({
      type: z.literal(type as ConnectorType),
      config: schema,
    }),
  ),
)

export const ConnectorStrictSchema = z.union(
  Object.entries(ConnectorStrictConfig).map(([type, schema]) =>
    z.object({
      type: z.literal(type as ConnectorType),
      config: schema,
    }),
  ),
)

export const ConnectorApiToFormSchema = z.union(
  Object.entries(ConnectorApiToFormConfig).map(([type, schema]) =>
    z.object({
      type: z.literal(type as ConnectorType),
      config: schema,
    }),
  ),
)

export const ConnectorFormToApiSchema = z.union(
  Object.entries(ConnectorFormToApiConfig).map(([type, schema]) =>
    z.object({
      type: z.literal(type as ConnectorType),
      config: schema,
    }),
  ),
)

export type ConnectorDraft = z.infer<typeof ConnectorLooseSchema>
