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
  id: z.number(),
  name: z.string().min(1, 'common.errors.nameRequired'),
  description: z.string().min(1, 'common.errors.descriptionRequired').max(150, 'common.errors.descriptionMaxLength'),
  connector: ConnectorTypeSchema,
  connection: ConnectionTypeSchema,
  lastActive: z.string(),
  tags: z.array(z.string()).default([]),
  status: DatasourceStatusSchema,
})

export type Datasource = z.infer<typeof DatasourceSchema>

/* Form schemas for create/edit */
export const DatasourceCreateFormSchema = DatasourceSchema.pick({ name: true })
export type DatasourceCreateFormData = z.infer<typeof DatasourceCreateFormSchema>

export const DatasourceFormSchema = DatasourceSchema.pick({
  id: true,
  name: true,
  description: true,
  tags: true,
  status: true,
}).required({ tags: true })
export type DatasourceFormData = z.infer<typeof DatasourceFormSchema>

/* API request types */
export type CreateDatasourceData = Omit<Datasource, 'id'>
export type UpdateDatasourceData = Partial<CreateDatasourceData> & { id: number }
