import z from 'zod'

export const CONNECTOR_TYPES = {
  MQTT: 'mqtt',
  SQL: 'sql',
  CSV: 'csv',
  REST: 'rest',
  S3: 's3',
} as const

export type ConnectorType = (typeof CONNECTOR_TYPES)[keyof typeof CONNECTOR_TYPES]

export const CONNECTION_TYPES = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  STATIC: 'static',
} as const

export type ConnectionType = (typeof CONNECTION_TYPES)[keyof typeof CONNECTION_TYPES]

export const DATASOURCE_STATUS_TYPES = {
  DRAFT: 'draft',
  AVAILABLE: 'available',
} as const

export type DatasourceStatusType = (typeof DATASOURCE_STATUS_TYPES)[keyof typeof DATASOURCE_STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

/* enums aus bestehenden constants */
export const ConnectorTypeSchema = enumFromConst(CONNECTOR_TYPES)
export const ConnectionTypeSchema = enumFromConst(CONNECTION_TYPES)
export const DatasourceStatusSchema = enumFromConst(DATASOURCE_STATUS_TYPES)

/* schema */
export const DatasourceSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  connector: ConnectorTypeSchema,
  connection: ConnectionTypeSchema,
  lastActive: z.string(),
  tags: z.array(z.string()),
  status: DatasourceStatusSchema,
})

export type Datasource = z.infer<typeof DatasourceSchema>
