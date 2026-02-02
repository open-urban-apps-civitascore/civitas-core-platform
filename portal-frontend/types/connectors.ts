import { z } from 'zod'

export const CONNECTOR_TYPES = {
  MQTT: 'mqtt',
  SQL: 'sql',
} as const

export const CONNECTOR_TYPE_KEYS = Object.fromEntries(
  Object.entries(CONNECTOR_TYPES).map(([k, v]) => [v, k]),
) as Record<ConnectorType, ConnectorTypeKey>

export type ConnectorTypeKey = keyof typeof CONNECTOR_TYPES
export type ConnectorType = (typeof CONNECTOR_TYPES)[ConnectorTypeKey]

const parseJsonArray = (value: string): unknown[] | null => {
  if (!value.trim().startsWith('[')) return null
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

export const parseStringToStringArray = (value: unknown): string[] | unknown => {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string') return value

  const trimmed = value.trim()
  if (!trimmed) return []

  const json = parseJsonArray(trimmed)
  if (json) return json

  return trimmed
    .split(',')
    .map(v => v.trim())
    .filter(Boolean)
}

export const MqttSchema = z.object({
  urls: z.preprocess(parseStringToStringArray, z.array(z.string()).min(1, 'common.errors.atLeast1Value')),

  topics: z.preprocess(parseStringToStringArray, z.array(z.string()).min(1, 'common.errors.atLeast1Value')),

  clientId: z.string().optional(),

  qos: z.enum(['0', '1', '2']).default('1'),

  connectTimeout: z.string().optional(),
  keepalive: z.string().optional(),

  tls: z
    .object({
      enabled: z.boolean().default(false),
    })
    .optional(),
})

export const SqlSelectSchema = z.object({
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

  dsn: z.string().min(1, 'common.errors.required'),
  table: z.string().min(1, 'common.errors.required'),

  columns: z
    .preprocess(parseStringToStringArray, z.array(z.string()).min(1, 'common.errors.atLeast1Value'))
    .default(['*']),

  where: z.string().optional(),
  argsMapping: z.array(z.any()).optional(),

  prefix: z.string().optional(),
  suffix: z.string().optional(),

  initFiles: z.array(z.string()).optional(),
  initStatement: z.string().optional(),

  connMaxIdleTime: z.string().optional(),
  connMaxLifeTime: z.string().optional(),
  connMaxIdle: z.number().int().nonnegative().default(2),
  connMaxOpen: z.number().int().nonnegative().default(0),
})

export const CONNECTOR_SCHEMAS = {
  mqtt: MqttSchema,
  sql: SqlSelectSchema,
} as const

export type ConnectorSchemaTypeKey = keyof typeof CONNECTOR_SCHEMAS
export type ConnectorConfigSchemaType = {
  [K in ConnectorType]: z.infer<(typeof CONNECTOR_SCHEMAS)[K]>
}

export const ConnectorSchema = z
  .object({
    type: z.enum(Object.keys(CONNECTOR_SCHEMAS) as [ConnectorType, ...ConnectorType[]]),
    config: z.unknown(),
  })
  .superRefine((value, ctx) => {
    const schema = CONNECTOR_SCHEMAS[value.type]
    const r = schema.safeParse(value.config)

    if (!r.success) {
      r.error.issues.forEach(issue => {
        ctx.addIssue({
          ...issue,
          path: ['config', ...issue.path],
        })
      })
    }
  })
