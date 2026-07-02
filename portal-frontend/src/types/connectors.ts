/* eslint-disable @typescript-eslint/naming-convention */
import { z } from 'zod'

import { CONNECTOR_TYPES } from '@/const/connectors'

export type ConnectorTypeKey = keyof typeof CONNECTOR_TYPES

export type ConnectorType = (typeof CONNECTOR_TYPES)[keyof typeof CONNECTOR_TYPES]

const QosSchema = z.preprocess(
  v => (typeof v === 'string' ? Number(v) : v),
  z.union([z.literal(0), z.literal(1), z.literal(2)]),
)

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
  client_id: z.string().nullable().optional(),
  qos: QosSchema.nullable().optional(),
  connect_timeout: z.string().nullable().optional(),
  keepalive: z.string().nullable().optional(),
  tls: z.object({ enabled: z.boolean() }).nullable().optional(),
  user: z.string().nullable().optional(),
  password: z.string().nullable().optional(),
})

/* Form schemas for edit */
const singleTopic = z.string().regex(/^[^,\s]*$/, 'datasources.errors.topicInvalidChars')

const MqttBaseSchema = z.object({
  urls: z.string().trim(),
  topics: singleTopic,
  client_id: z.string().trim(),
  qos: QosSchema,
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

export const MqttLooseSchema = MqttBaseSchema.partial().extend({
  urls: brokerUrlArray(z.array(z.string()).optional()),
  topics: singleTopic
    .optional()
    .transform(parseStringArray)
    .pipe(z.array(z.string()).max(1, 'datasources.errors.topicSingle').optional()),
})

export const MqttStrictSchema = MqttBaseSchema.partial()
  .required({ qos: true })
  .extend({
    urls: brokerUrlArray(z.array(z.string()).min(1, 'common.errors.required')),
    topics: singleTopic
      .transform(v => parseStringArray(v) ?? [])
      .pipe(z.array(z.string()).min(1, 'common.errors.required').max(1, 'datasources.errors.topicSingle')),
  })

export const MqttApiToFormSchema = MqttApiResponseSchema.transform(({ urls, topics, tls, qos, ...rest }) => ({
  ...Object.fromEntries(
    Object.entries(rest).map(([k, v]) => [k, typeof v === 'string' && v.trim() === '' ? undefined : (v ?? undefined)]),
  ),
  urls: urls?.join(', '),
  topics: topics?.join(', '),
  qos: qos == null ? undefined : String(qos),
  tls: tls?.enabled ?? false,
}))

export const SqlApiResponseSchema = z.object({
  driver: z
    .enum(['postgres', 'mysql', 'clickhouse', 'mssql', 'sqlite', 'oracle', 'snowflake', 'trino', 'gocosmos', 'spanner'])
    .nullable(),
  dsn: z.string().nullable(),
  table: z.string().nullable(),
  columns: z.array(z.string()).nullable(),
  where: z.string().nullable(),
  prefix: z.string().nullable(),
  suffix: z.string().nullable(),
  init_statement: z.string().nullable(),
  conn_max_idle_time: z.string().nullable(),
  conn_max_life_time: z.string().nullable(),
  conn_max_idle: z.number().int().nonnegative().nullable(),
  conn_max_open: z.number().int().nonnegative().nullable(),
  user: z.string().nullable(),
  password: z.string().nullable(),
})

const SqlBaseSchema = z.object({
  driver: z.enum([
    'postgres',
    'mysql',
    'clickhouse',
    'mssql',
    'sqlite',
    'oracle',
    'snowflake',
    'trino',
    'gocosmos',
    'spanner',
  ]),
  dsn: z.string().trim(),
  table: z.string().trim(),
  columns: z.string().trim(),
  where: z.string().trim(),
  prefix: z.string().trim(),
  suffix: z.string().trim(),
  init_statement: z.string().trim(),
  conn_max_idle_time: z.string().trim(),
  conn_max_life_time: z.string().trim(),
  conn_max_idle: z.preprocess(
    v => (v === '' || v === undefined ? undefined : Number(v)),
    z.number().int().nonnegative(),
  ),
  conn_max_open: z.preprocess(
    v => (v === '' || v === undefined ? undefined : Number(v)),
    z.number().int().nonnegative(),
  ),
  user: z.string().trim(),
  password: z.string().trim(),
})

export const SqlLooseSchema = SqlBaseSchema.partial().extend({
  dsn: URISchema.optional(),
  columns: z.preprocess(parseStringArray, z.array(z.string()).optional()),
  conn_max_idle: z.preprocess(
    v => (v === '' || v === undefined ? undefined : Number(v)),
    z.number().int().nonnegative().optional(),
  ),
  conn_max_open: z.preprocess(
    v => (v === '' || v === undefined ? undefined : Number(v)),
    z.number().int().nonnegative().optional(),
  ),
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
    conn_max_idle: z.preprocess(
      v => (v === '' || v === undefined ? undefined : Number(v)),
      z.number().int().nonnegative().optional(),
    ),
    conn_max_open: z.preprocess(
      v => (v === '' || v === undefined ? undefined : Number(v)),
      z.number().int().nonnegative().optional(),
    ),
  })

export const SqlApiToFormSchema = SqlApiResponseSchema.transform(({ columns, ...rest }) => ({
  ...Object.fromEntries(Object.entries(rest).map(([k, v]) => [k, v ?? undefined])),
  columns: columns?.join(', '),
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
  ])

export const ConnectorApiResponseSchema = connectorUnion({ MQTT: MqttApiResponseSchema, SQL: SqlApiResponseSchema })
export const ConnectorLooseSchema = connectorUnion({ MQTT: MqttLooseSchema, SQL: SqlLooseSchema })
export const ConnectorStrictSchema = connectorUnion({ MQTT: MqttStrictSchema, SQL: SqlStrictSchema })
export const ConnectorApiToFormSchema = connectorUnion({ MQTT: MqttApiToFormSchema, SQL: SqlApiToFormSchema })
export const ConnectorFormToApiSchema = connectorUnion({ MQTT: MqttFormToApiSchema, SQL: SqlLooseSchema })

export type ConnectorDraft = z.infer<typeof ConnectorLooseSchema>
