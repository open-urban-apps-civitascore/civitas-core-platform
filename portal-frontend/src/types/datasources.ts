import z from 'zod'

import { CONNECTOR_TYPES } from '@/const/connectors'

import { AssignmentScopedInput } from './assignments'
import { MAX_DESCRIPTION_LENGTH, MAX_NAME_LENGTH, MIN_NAME_LENGTH, STATUS_TYPES, WithId } from './common'
import { ConnectorApiToFormSchema, ConnectorLooseSchema, ConnectorStrictSchema } from './connectors'
import { DatastructureVersionSummaryApiResponseSchema } from './datastructures'

export type DatasourceTab = 'basicInfo' | 'connector' | 'dataStructure' | 'datapools' | 'accessManagement'

export const DATASOURCE_STATUS_TYPES = {
  DRAFT: 'DRAFT',
  AVAILABLE: 'AVAILABLE',
} as const satisfies Partial<typeof STATUS_TYPES>

export type DatasourceStatusType = (typeof DATASOURCE_STATUS_TYPES)[keyof typeof DATASOURCE_STATUS_TYPES]

const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const ConnectorTypeSchema = enumFromConst(CONNECTOR_TYPES)
export const DatasourceStatusSchema = enumFromConst(DATASOURCE_STATUS_TYPES)

export type FormFieldType = 'input' | 'textArea' | 'select' | 'checkbox'

export type FormInputType = 'text' | 'password'

export type ConnectorField = {
  key: string
  type: FormFieldType
  inputType?: FormInputType
  label: { label: string; labelHint: string | null }
  options?: string[]
  defaultValue?: unknown
  required?: boolean
  placeholder?: string
  rows?: number
  expert?: boolean
}

export const DATAPOOL_SCOPE_TYPES = {
  NONE: 'NONE',
  ALL: 'ALL',
  SPECIFIC: 'SPECIFIC',
} as const

export type DatapoolScopeType = (typeof DATAPOOL_SCOPE_TYPES)[keyof typeof DATAPOOL_SCOPE_TYPES]

export const DatapoolScopeSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal(DATAPOOL_SCOPE_TYPES.NONE) }),
  z.object({ type: z.literal(DATAPOOL_SCOPE_TYPES.ALL) }),
  z.object({ type: z.literal(DATAPOOL_SCOPE_TYPES.SPECIFIC), datapoolIds: z.array(z.string()) }),
])

export type DatapoolScope = z.infer<typeof DatapoolScopeSchema>

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
  datapoolScope: DatapoolScopeSchema,
})

export type Datasource = z.infer<typeof DatasourceApiResponseSchema>

/* Form schemas for edit */
export const DatasourceBaseFormSchema = z.object({
  id: z.string(),
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, 'common.errors.nameRequired')
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z.string().trim().max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  dataSourceStatus: DatasourceStatusSchema,
  connectorType: ConnectorTypeSchema.optional(),
  configuration: z.record(z.string(), z.unknown()).optional(),
  dataStructureVersionId: z.string().trim().min(1, 'datasources.errors.required').nullable(),
  datapoolScope: DatapoolScopeSchema.optional(),
})

export type DatasourceBaseFormData = z.infer<typeof DatasourceBaseFormSchema>

export const DatasourceFormDraftSchema = DatasourceBaseFormSchema.superRefine((data, ctx) => {
  if (!data.connectorType || !data.configuration) return
  const result = ConnectorLooseSchema.safeParse(data)
  if (!result.success) {
    result.error.issues.forEach(issue => ctx.addIssue({ ...issue }))
  }
})

export const DatasourceFormAvailableSchema = DatasourceBaseFormSchema.extend({
  description: z
    .string()
    .trim()
    .min(1, 'common.errors.required')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  connectorType: ConnectorTypeSchema,
  configuration: z.record(z.string(), z.unknown()),
  dataStructureVersionId: z.string().trim().min(1, 'datasources.errors.required'),
}).superRefine((data, ctx) => {
  const result = ConnectorStrictSchema.safeParse(data)
  if (!result.success) {
    result.error.issues.forEach(issue => ctx.addIssue({ ...issue }))
  }
})

export type DatasourceFormDraft = z.input<typeof DatasourceFormDraftSchema>

export const DatasourceApiToFormSchema = DatasourceApiResponseSchema.transform(
  ({ id, name, description, dataSourceStatus, dataStructureVersion, connectorType, configuration, datapoolScope }) => {
    const connectorParsed =
      connectorType && configuration ? ConnectorApiToFormSchema.safeParse({ connectorType, configuration }) : null
    if (connectorParsed && !connectorParsed.success) {
      console.error('ConnectorApiToFormSchema parse failed:', connectorParsed.error.issues)
    }
    return {
      id,
      name: name ?? '',
      description: description ?? '',
      dataSourceStatus,
      dataStructureVersionId: dataStructureVersion?.id,
      datapoolScope,
      ...(connectorParsed?.success ? connectorParsed.data : {}),
    } as DatasourceFormDraft
  },
)

export const DatasourceCreateFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, 'common.errors.nameRequired')
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z.string().trim().max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
})

export type DatasourceCreateData = z.infer<typeof DatasourceCreateFormSchema>
export type DatasourcePatchData = Partial<DatasourceFormDraft> & WithId & { assignments?: AssignmentScopedInput[] }
export type DatasourcePutData = Partial<DatasourceFormDraft> &
  WithId & { name: string } & { assignments?: AssignmentScopedInput[] }
