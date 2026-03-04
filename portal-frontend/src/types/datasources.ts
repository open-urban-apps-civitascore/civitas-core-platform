import z from 'zod'

import { CONNECTOR_TYPES } from '@/const/connectors'

import { AssignmentScopedInputSchema } from './assignments'
import { STATUS_TYPES, WithId } from './common'
import { ConnectorApiToFormSchema, ConnectorLooseSchema, ConnectorStrictSchema } from './connectors'
import { DatastructureVersionSummaryApiResponseSchema } from './datastructures'

export type DatasourceTab = 'basicInfo' | 'connector' | 'dataStructure' | 'accessPermissions'

export type DatasourceStatusType = (typeof STATUS_TYPES)[keyof typeof STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const ConnectorTypeSchema = enumFromConst(CONNECTOR_TYPES)
export const DatasourceStatusSchema = enumFromConst(STATUS_TYPES)

export type FormFieldType = 'input' | 'textArea' | 'select' | 'checkbox'

export type ConnectorField = {
  key: string
  type: FormFieldType
  label: { label: string; labelHint: string | null }
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  placeholder?: string
  rows?: number
  expert?: boolean
}

export const DatasourceApiResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  dataSourceStatus: DatasourceStatusSchema,
  connectorType: ConnectorTypeSchema.nullable(),
  configuration: z.record(z.string(), z.unknown()).nullable(),
  dataStructureVersion: DatastructureVersionSummaryApiResponseSchema.nullable(),
  inUse: z.boolean(),
})

export type Datasource = z.infer<typeof DatasourceApiResponseSchema>

/* Form schemas for edit */
export const DatasourceBaseFormSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, 'common.errors.nameRequired'),
  description: z.string().trim().max(150, 'common.errors.descriptionMaxLength'),
  dataStructureVersionId: z.string().trim().min(1, 'datasources.errors.required'),
  assignments: AssignmentScopedInputSchema.array(),
})

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export const DatasourceFormDraftSchema = DatasourceBaseFormSchema.partial()
  .extend({
    connectorType: ConnectorTypeSchema.optional(),
    configuration: z.record(z.string(), z.unknown()).optional(),
  })
  .superRefine((data, ctx) => {
    if (!data.connectorType || !data.configuration) return
    const result = ConnectorLooseSchema.safeParse(data)
    if (!result.success) {
      result.error.issues.forEach(issue => ctx.addIssue({ ...issue }))
    }
  })

export const DatasourceFormAvailableSchema = DatasourceBaseFormSchema.partial()
  .required({
    name: true,
    dataStructureVersionId: true,
  })
  .extend({
    connectorType: ConnectorTypeSchema,
    configuration: z.record(z.string(), z.unknown()),
  })
  .superRefine((data, ctx) => {
    const result = ConnectorStrictSchema.safeParse(data)
    if (!result.success) {
      result.error.issues.forEach(issue => ctx.addIssue({ ...issue }))
    }
  })

export type DatasourceFormDraft = z.input<typeof DatasourceFormDraftSchema>

export const DatasourceApiToFormSchema = DatasourceApiResponseSchema.transform(
  ({ id, name, description, dataStructureVersion, connectorType, configuration }) => {
    const connectorParsed =
      connectorType && configuration ? ConnectorApiToFormSchema.safeParse({ connectorType, configuration }) : null
    if (connectorParsed && !connectorParsed.success) {
      console.error('ConnectorApiToFormSchema parse failed:', connectorParsed.error.issues)
    }
    return {
      id,
      name: name ?? '',
      description: description ?? '',
      dataStructureVersionId: dataStructureVersion?.id,
      ...(connectorParsed?.success ? connectorParsed.data : {}),
    } as DatasourceFormDraft
  },
)

export type DatasourceCreateData = { name: string }
export type DatasourceUpdateData = DatasourceFormDraft & WithId
