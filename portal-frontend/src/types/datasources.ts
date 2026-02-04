import z from 'zod'

import type { CONNECTORS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { CONNECTION_TYPES, CONNECTOR_TYPES, DATASOURCE_STATUS_TYPES } from '@/const/connectors'

import { ConnectorApiSchema, ConnectorLooseSchema, ConnectorStrictSchema } from './connectors'

export type ConnectionType = (typeof CONNECTION_TYPES)[keyof typeof CONNECTION_TYPES]

export type DatasourceStatusType = (typeof DATASOURCE_STATUS_TYPES)[keyof typeof DATASOURCE_STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const ConnectorTypeSchema = enumFromConst(CONNECTOR_TYPES)
export const ConnectionTypeSchema = enumFromConst(CONNECTION_TYPES)
export const DatasourceStatusSchema = enumFromConst(DATASOURCE_STATUS_TYPES)

export type FormFieldType = 'input' | 'textArea' | 'select' | 'checkbox'

export type ConnectorField = {
  key: string
  type: FormFieldType
  label: { label: string; labelHint: string | null }
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  placeholder: string
}

/* schema */
export const DatasourceSchema = z.object({
  id: z.number({
    error: issue => (issue.input === undefined ? 'common.errors.required' : 'common.errors.invalidNumber'),
  }),
  name: z.string().min(1, 'common.errors.nameRequired'),
  description: z.string().min(1, 'common.errors.descriptionRequired').max(150, 'common.errors.descriptionMaxLength'),
  connection: ConnectionTypeSchema,
  lastActive: z.string(),
  tags: z.array(z.string()).default([]),
  status: DatasourceStatusSchema,
  connector: ConnectorApiSchema.nullable(),
})

export type Datasource = z.infer<typeof DatasourceSchema>

/* Form schemas for create */
export const DatasourceCreateFormSchema = DatasourceSchema.pick({ name: true })
export type DatasourceCreateFormData = z.infer<typeof DatasourceCreateFormSchema>

/* Form schemas for edit */
export const DatasourceBaseFormSchema = DatasourceSchema.pick({
  id: true,
  name: true,
  description: true,
  tags: true,
  status: true,
}).required({ tags: true })

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export type CreateDatasourceData = Omit<Datasource, 'id'>
export type UpdateDatasourceData = Partial<CreateDatasourceData> & { id: number }

export type ConnectorNodeDefs = typeof CONNECTORS
export type ConnectorDefaultsByType = {
  [K in keyof ConnectorNodeDefs]: {
    [P in ConnectorNodeDefs[K]['properties'][number] as P['key']]: P['defaultValue']
  }
}

export const DatasourceFormDraftSchema = DatasourceBaseFormSchema.extend({ connector: ConnectorLooseSchema.nullable() })
export const DatasourceFormAvailableSchema = DatasourceBaseFormSchema.extend({ connector: ConnectorStrictSchema })
export const DatasourceApiDataSchema = DatasourceBaseFormSchema.extend({ connector: ConnectorApiSchema.optional() })

export type DatasourceFormDraft = z.input<typeof DatasourceFormDraftSchema>
export type DatasourceFormAvailable = z.output<typeof DatasourceFormAvailableSchema>
export type DatasourceApiData = z.output<typeof DatasourceApiDataSchema>
