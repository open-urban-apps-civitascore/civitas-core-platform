import z from 'zod'

import { UMLDiagram, UMLEdge, UMLNode } from '@/components/uml-modeler/types/diagram'
import { enumFromConst } from '@/utils/common'

import { AssignmentSchema, AssignmentScopedInput } from './assignments'
import {
  ItemSchema,
  MAX_DESCRIPTION_LENGTH,
  MAX_NAME_LENGTH,
  MIN_DESCRIPTION_LENGTH,
  MIN_NAME_LENGTH,
  STATUS_TYPES,
  WithId,
} from './common'

export const DATASTRUCTURE_STATUS_TYPES = {
  DRAFT: 'DRAFT',
  AVAILABLE: 'AVAILABLE',
} as const satisfies Partial<typeof STATUS_TYPES>

export const DatastructureStatusEnum = enumFromConst(DATASTRUCTURE_STATUS_TYPES)

export type DatastructureStatusType = (typeof DATASTRUCTURE_STATUS_TYPES)[keyof typeof DATASTRUCTURE_STATUS_TYPES]

export type DatastructureTab = 'basicInfo' | 'versions' | 'accessManagement'

export type DatastructureVersionTab = 'structure' | 'versionInfo'

export const UMLModelStylesPayloadSchema = z.object({
  viewport: z.object({ x: z.number(), y: z.number(), zoom: z.number() }).optional(),
  nodePositions: z.record(z.string(), z.object({ x: z.number(), y: z.number() })),
})

// DATASTRUCTURE VERSIONS

export const DatastructureVersionApiResponseSchema = z.object({
  id: z.string(),
  // Assigned by the registry when the model is stored, so a version that carries no model yet
  // — a draft whose diagram is still empty — has no number.
  version: z.string().nullable(),
  description: z.string().nullable(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  modelName: z.string().nullable(),
  // Versioned CORE URN of this version's model (DataStructure) artifact in Model Forge.
  modelUrn: z.string().nullable().optional(),
  model: z.record(z.string(), z.unknown()).nullable(),
  styles: z.custom<UMLDiagram>().nullable(),
  // Versioned CORE URNs of the published structures this version was built from. The import pins
  // the version, so a later version of a structure leaves this one untouched.
  importedStructureUrns: z.array(z.string()).nullable().optional(),
  inUse: z.boolean().optional(),
  inUseByReleased: z.boolean().optional(),
  dataStructure: ItemSchema,
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export const DatastructureVersionSummaryApiResponseSchema = z.object({
  id: z.string(),
  version: z.string().nullable(),
  description: z.string().nullable(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  createdAt: z.string(),
  modifiedAt: z.string(),
  dataStructureId: z.string(),
  inUse: z.boolean().optional(),
  inUseByReleased: z.boolean().optional(),
})

// The version is assigned by the registry on store, never authored: the form carries it only to
// display the assigned value, so it has no shape to enforce and is empty until the first store.
const versionSchema = z.string()

export const DatastructureVersionFormDraftSchema = z.object({
  id: z.string(),
  version: versionSchema,
  description: z.string().trim(),
  dataStructureVersionStatus: DatastructureStatusEnum,
  modelName: z.string().trim().nullable(),
  nodes: z.array(z.custom<UMLNode>()),
  edges: z.array(z.custom<UMLEdge>()),
})

export const DatastructureVersionFormAvailableSchema = DatastructureVersionFormDraftSchema.extend({
  description: z.string().trim().min(1, 'common.errors.required'),
  modelName: z.string().trim().min(1, 'common.errors.required'),
  // Available datastructure models must have at least one node
  nodes: z.array(z.custom<UMLNode>()).min(1),
  edges: z.array(z.custom<UMLEdge>()),
})

export const DatastructureVersionCreateSchema = z.object({
  description: z.string().trim().max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength').optional(),
  dataStructureVersionStatus: DatastructureStatusEnum.optional(),
  modelName: z.string().trim().nullable().optional(),
  model: z.record(z.string(), z.unknown()).nullable().optional(),
  styles: z.custom<UMLDiagram>().nullable().optional(),
  importedStructureUrns: z.array(z.string()).nullable().optional(),
})

export type DatastructureVersion = z.infer<typeof DatastructureVersionApiResponseSchema>
export type DatastructureVersionSummary = z.infer<typeof DatastructureVersionSummaryApiResponseSchema>

export type DatastructureVersionFormData = z.infer<typeof DatastructureVersionFormDraftSchema>
export type DatastructureVersionCreateData = z.infer<typeof DatastructureVersionCreateSchema>
export type DatastructureVersionPutData = DatastructureVersionCreateData & WithId
export type DatastructureVersionPatchData = Partial<DatastructureVersionCreateData> & WithId

export type DatastructureVersionsListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatusType
  versionNumber: string | null
  inUseByReleased?: boolean
}

// DATASTRUCTURE TYPES

export const DatastructureApiResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  dataStructureStatus: DatastructureStatusEnum,
  assignments: z.array(AssignmentSchema).optional(),
  inUse: z.boolean().optional(),
  inUseByReleased: z.boolean().optional(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  dataStructureVersions: z.array(DatastructureVersionSummaryApiResponseSchema),
})

export type Datastructure = z.infer<typeof DatastructureApiResponseSchema>

export const DatastructureApiResponseSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
})

export const DatastructureFormDraftSchema = z.object({
  id: z.string(),
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, 'common.errors.nameRequired')
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  dataStructureStatus: DatastructureStatusEnum,
})

export const DatastructureFormAvailableSchema = DatastructureFormDraftSchema.extend({
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
})

export type DatastructureFormDraft = z.infer<typeof DatastructureFormDraftSchema>
export type DatastructureFormAvailable = z.infer<typeof DatastructureFormAvailableSchema>

export const DatastructureCreateFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, 'common.errors.nameRequired')
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
})

export const DatastructureCreateDataSchema = z.object({
  name: z
    .string()
    .trim()
    .min(MIN_NAME_LENGTH, 'common.errors.nameRequired')
    .max(MAX_NAME_LENGTH, 'common.errors.nameMaxLength'),
  description: z
    .string()
    .trim()
    .min(MIN_DESCRIPTION_LENGTH, 'common.errors.descriptionRequired')
    .max(MAX_DESCRIPTION_LENGTH, 'common.errors.descriptionMaxLength'),
  assignments: z.array(AssignmentSchema).optional(),
})

export type DatastructureCreateFormData = z.infer<typeof DatastructureCreateFormSchema>
export type DatastructureCreateData = z.infer<typeof DatastructureCreateDataSchema>

export type DatastructurePutData = DatastructureFormDraft & {
  assignments?: AssignmentScopedInput[]
}
export type DatastructurePatchData = Partial<DatastructureCreateData> & WithId

// DATASTRUCTURE LIST DATA

export type DatastructuresListData = {
  id: string
  name: string
  description: string
  status: DatastructureStatusType
  versionNumber: string | null
  versions: DatastructuresListData[]
  inUse?: boolean
  inUseByReleased?: boolean
}
