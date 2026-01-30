import z from 'zod'

import type { NODE_DEFS } from '@/app/(main)/datasources/components/connector-tab/connector_sources'

export const CONNECTOR_TYPES = {
  MQTT: 'mqtt',
  SQL: 'sql',
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

export const ConnectorTypeSchema = enumFromConst(CONNECTOR_TYPES)
export const ConnectionTypeSchema = enumFromConst(CONNECTION_TYPES)
export const DatasourceStatusSchema = enumFromConst(DATASOURCE_STATUS_TYPES)

export type FormFieldType = 'input' | 'textArea' | 'select' | 'checkbox'

export type ConnectorConfig = {
  key: string
  type: FormFieldType
  label: string
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  placeholder: string
}

export const buildConnectorConfigSchema = (config: ConnectorConfig[]) => {
  const shape: Record<string, z.ZodTypeAny> = {}

  config.forEach(p => {
    let field: z.ZodTypeAny = z.any()
    const isOptional = typeof p.placeholder === 'string' && p.placeholder.toLowerCase().includes('optional')

    if (typeof p.defaultValue === 'number') {
      field = z.preprocess(val => {
        if (typeof val === 'string') {
          const trimmed = val.trim()
          if (trimmed === '') return undefined
          return Number(trimmed)
        }
        return val
      }, z.number({
        required_error: 'common.errors.required',
        invalid_type_error: 'common.errors.invalidNumber',
      }))
    } else if (typeof p.defaultValue === 'string') {
      field = z.string().min(1, 'common.errors.required')
    } else if (typeof p.defaultValue === 'boolean') {
      field = z.boolean()
    } else {
      // Fallback to type if defaultValue is missing/unknown
      if (p.type === 'input' || p.type === 'textArea' || p.type === 'select') {
        field = z.string().min(1, 'common.errors.required')
      }
      if (p.type === 'checkbox') field = z.boolean()
    }

    if (isOptional) {
      field = z.preprocess(val => (val === '' ? undefined : val), field).optional()
    }

    shape[p.key] = field
  })

  return z.object(shape)
}

export const buildConnectorDefaultConfig = (config: ConnectorConfig[]) => {
  const defaults: Record<string, unknown> = {}

  config.forEach(p => {
    defaults[p.key] = p.defaultValue
  })

  return defaults
}

/* schema */
export const DatasourceBaseSchema = z.object({
  id: z.number({
    required_error: 'common.errors.required',
    invalid_type_error: 'common.errors.invalidNumber',
  }),
  name: z.string().min(1, 'common.errors.nameRequired'),
  description: z.string().min(1, 'common.errors.descriptionRequired').max(150, 'common.errors.descriptionMaxLength'),
  connection: ConnectionTypeSchema,
  lastActive: z.string(),
  tags: z.array(z.string()).default([]),
  status: DatasourceStatusSchema,
  connector: z.object({ type: ConnectorTypeSchema.optional(), config: z.record(z.string(), z.unknown()).nullable() }),
})

export type BaseDatasource = z.infer<typeof DatasourceBaseSchema>

/* Form schemas for create/edit */
export const DatasourceCreateFormSchema = DatasourceBaseSchema.pick({ name: true })
export type DatasourceCreateFormData = z.infer<typeof DatasourceCreateFormSchema>

export const DatasourceBaseFormSchema = DatasourceBaseSchema.pick({
  id: true,
  name: true,
  description: true,
  tags: true,
  status: true,
}).required({ tags: true })

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export const buildConnectorShema = (config: ConnectorConfig[], status: DatasourceStatusType) => {
  const isAvailable = status === DATASOURCE_STATUS_TYPES.AVAILABLE
  return z.object({
    type: isAvailable ? ConnectorTypeSchema : ConnectorTypeSchema.optional(),
    config: isAvailable ? buildConnectorConfigSchema(config) : buildConnectorConfigSchema(config).nullable(),
  })
}


export const buildDatasourceFormSchema = (config: ConnectorConfig[], status: DatasourceStatusType) =>
  DatasourceBaseFormSchema.extend({
    connector: buildConnectorShema(config, status),
  })

export type DatasourceFormData = z.infer<ReturnType<typeof buildDatasourceFormSchema>>

/* API request types */
export type CreateDatasourceData = Omit<BaseDatasource, 'id'>
export type UpdateDatasourceData = Partial<CreateDatasourceData> & { id: number }

/* Inferred defaults from connector_sources */
export type ConnectorNodeDefs = typeof NODE_DEFS
export type ConnectorDefaultsByType = {
  [K in keyof ConnectorNodeDefs]: {
    [P in ConnectorNodeDefs[K]['properties'][number] as P['key']]: P['defaultValue']
  }
}
