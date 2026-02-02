import z from 'zod'

import type { NODE_DEFS } from '@/app/(main)/datasources/components/connector-tab/connector_sources'

import { CONNECTOR_TYPES, ConnectorSchema } from './connectors'

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

/* schema */
export const DatasourceBaseSchema = z.object({
  id: z.number({
    error: issue => (issue.input === undefined ? 'common.errors.required' : 'common.errors.invalidNumber'),
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

/* Form schemas for create */
export const DatasourceCreateFormSchema = DatasourceBaseSchema.pick({ name: true })
export type DatasourceCreateFormData = z.infer<typeof DatasourceCreateFormSchema>

/* Form schemas for edit */
export const DatasourceBaseFormSchema = DatasourceBaseSchema.pick({
  id: true,
  name: true,
  description: true,
  tags: true,
  status: true,
}).required({ tags: true })

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export type DatasourceFormData = z.infer<typeof DatasourceFormSchema>

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

export const DatasourceFormSchema = DatasourceBaseFormSchema.extend({
  connector: ConnectorSchema,
})
