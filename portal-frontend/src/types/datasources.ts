import z from 'zod'

import { CONNECTION_TYPES, CONNECTOR_TYPES, DATASOURCE_STATUS_TYPES } from '@/const/connectors'

import { WithId } from './common'
import {
  ConnectorApiResponseSchema,
  ConnectorFormToApiSchema,
  ConnectorLooseSchema,
  ConnectorStrictSchema,
} from './connectors'

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

export const DatasourceApiResponseSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  description: z.string().trim(),
  connection: ConnectionTypeSchema,
  lastActive: z.string(),
  status: DatasourceStatusSchema,
  connector: ConnectorApiResponseSchema.nullable(),
})

export type Datasource = z.infer<typeof DatasourceApiResponseSchema>

/* Form schemas for edit */
export const DatasourceBaseFormSchema = DatasourceApiResponseSchema.pick({
  id: true,
  name: true,
  description: true,
  status: true,
})

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export const DatasourceFormDraftSchema = DatasourceBaseFormSchema.extend({ connector: ConnectorLooseSchema.nullable() })
export const DatasourceFormAvailableSchema = DatasourceBaseFormSchema.extend({
  name: z.string().trim().min(1, 'common.errors.descriptionRequired'),
  description: z
    .string()
    .trim()
    .min(1, 'common.errors.descriptionRequired')
    .max(150, 'common.errors.descriptionMaxLength'),
  connector: ConnectorStrictSchema,
})
export const DatasourceFormToApiSchema = DatasourceBaseFormSchema.partial().extend({
  connector: ConnectorFormToApiSchema.nullable().optional(),
})

export type DatasourceFormToApiData = z.infer<typeof DatasourceFormToApiSchema>

export type DatasourceFormDraft = z.input<typeof DatasourceFormDraftSchema>

export const DatasourceCreateFormSchema = DatasourceApiResponseSchema.pick({ name: true })
export type DatasourceCreateFormData = z.infer<typeof DatasourceCreateFormSchema>
export type DatasourceCreateData = Omit<Datasource, 'id'>

export type DatasourceUpdateData = Partial<z.infer<typeof DatasourceFormToApiSchema>> & WithId
