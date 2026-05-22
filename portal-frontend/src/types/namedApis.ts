import { z } from 'zod'

export const SLUG_MAX_LENGTH = 32
export const NAMED_API_DESCRIPTION_MAX_LENGTH = 150

// Mirrors backend NamedApiInputDTO @Pattern: lowercase alphanumeric, internal hyphens only,
// no leading/trailing dash. Single-character slugs allowed.
export const SLUG_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/

// Slugs that would collide with sibling routes under /datasets/{id}/...
export const RESERVED_SLUGS = ['apis'] as const

export const API_STANDARDS = {
  WFS: 'WFS',
  WMS: 'WMS',
  STA: 'STA',
  CUSTOM: 'CUSTOM',
} as const

export type ApiStandard = (typeof API_STANDARDS)[keyof typeof API_STANDARDS]

export const API_TYPE_QUERY = {
  SENSORTHINGS: 'sensorthings',
  WFS_WMS: 'wfs-wms',
} as const

export type ApiTypeQuery = (typeof API_TYPE_QUERY)[keyof typeof API_TYPE_QUERY]

export const isApiTypeQuery = (value: unknown): value is ApiTypeQuery =>
  value === API_TYPE_QUERY.SENSORTHINGS || value === API_TYPE_QUERY.WFS_WMS

// FE-hardcoded for now. Follow-up: derive from dataset's data-flow state.
export const PERSISTENCE_OPTIONS = {
  FROST: { value: 'frost', label: 'FROST Server' },
  POSTGIS: { value: 'postgis', label: 'PostGIS Geo Persistence' },
} as const

export const DEFAULTS_BY_TYPE: Record<
  ApiTypeQuery,
  {
    standard: ApiStandard
    defaultSlug: string
    persistenceValue: string
    persistenceLabel: string
  }
> = {
  [API_TYPE_QUERY.SENSORTHINGS]: {
    standard: API_STANDARDS.STA,
    defaultSlug: 'sta',
    persistenceValue: PERSISTENCE_OPTIONS.FROST.value,
    persistenceLabel: PERSISTENCE_OPTIONS.FROST.label,
  },
  [API_TYPE_QUERY.WFS_WMS]: {
    standard: API_STANDARDS.WFS,
    defaultSlug: 'wfswms',
    persistenceValue: PERSISTENCE_OPTIONS.POSTGIS.value,
    persistenceLabel: PERSISTENCE_OPTIONS.POSTGIS.label,
  },
}

export const NamedApiSchema = z.object({
  id: z.string().optional(),
  name: z.string(),
  slug: z.string(),
  standard: z.enum([API_STANDARDS.WFS, API_STANDARDS.WMS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
  version: z.string().optional(),
  description: z.string().optional(),
  previewUrl: z.string().optional(),
})

export type NamedApi = z.infer<typeof NamedApiSchema>

export const BoundingBoxPayloadSchema = z.object({
  minX: z.number(),
  minY: z.number(),
  maxX: z.number(),
  maxY: z.number(),
  crs: z.string(),
})

export const NamedApiPayloadSchema = z.object({
  name: z.string(),
  slug: z.string(),
  standard: z.enum([API_STANDARDS.WFS, API_STANDARDS.WMS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
  version: z.string().optional(),
  description: z.string().optional(),
})

export type NamedApiPayload = z.infer<typeof NamedApiPayloadSchema>

interface BuildSchemaArgs {
  existingSlugs: string[]
}

export const NamedApiBaseInfoFormSchema = ({ existingSlugs }: BuildSchemaArgs) => {
  const normalizedExisting = existingSlugs.map(s => s.toLowerCase())
  return z.object({
    name: z.string().trim().min(1, 'datasets.overview.completion.apis.config.errors.name.required'),
    slug: z
      .string()
      .min(1, 'datasets.overview.completion.apis.config.errors.slug.required')
      .max(SLUG_MAX_LENGTH, 'datasets.overview.completion.apis.config.errors.slug.tooLong')
      .regex(SLUG_PATTERN, 'datasets.overview.completion.apis.config.errors.slug.invalidFormat')
      .refine(
        s => !RESERVED_SLUGS.includes(s as (typeof RESERVED_SLUGS)[number]),
        'datasets.overview.completion.apis.config.errors.slug.reserved',
      )
      .refine(
        s => !normalizedExisting.includes(s.toLowerCase()),
        'datasets.overview.completion.apis.config.errors.slug.notUnique',
      ),
    description: z
      .string()
      .trim()
      .max(NAMED_API_DESCRIPTION_MAX_LENGTH, 'datasets.overview.completion.apis.config.errors.description.tooLong')
      .optional()
      .or(z.literal('')),
    persistence: z.string().min(1, 'datasets.overview.completion.apis.config.errors.persistence.required'),
  })
}

// ============================================================================
// Types for Sta-Api
// ============================================================================

export const StaApiFormSchema = ({ existingSlugs }: BuildSchemaArgs) =>
  z.object({
    type: z.literal(API_TYPE_QUERY.SENSORTHINGS),
    baseInfo: NamedApiBaseInfoFormSchema({ existingSlugs }),
  })

export type StaApiFormData = z.input<ReturnType<typeof StaApiFormSchema>>
export type StaApiPayloadData = {
  baseInfo: NamedApiPayload
}

// ============================================================================
// Types for Layer config
// ============================================================================

export const BoundingBoxResponseSchema = z.object({
  minx: z.number(),
  miny: z.number(),
  maxx: z.number(),
  maxy: z.number(),
  crs: z.string(),
})

const LayerBaseSchema = z.object({
  dataSinkId: z.uuid(),
  layerName: z.string(),
  title: z.string(),
  description: z.string().optional(),
  keywords: z.array(z.string()).optional(),
  attribute: z.array(z.string()),
  geometryColumnRef: z.string(),
  cqlFilter: z.string().nullable(),
  alternativeStyleIds: z.array(z.uuid()),
  crs: z.string(),
})

export const LayerSchema = LayerBaseSchema.extend({
  id: z.uuid(),
  datasetId: z.uuid(),
  defaultStyleId: z.uuid().nullable(),
  geometryType: z.string(),
  nativeCRS: z.string(),
  bboxAutoCalculate: z.boolean(),
  nativeBoundingBox: BoundingBoxResponseSchema,
  latLonBoundingBox: BoundingBoxResponseSchema,
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export const LayerPayloadSchema = LayerBaseSchema.extend({
  defaultStyleId: z.uuid().optional().nullable(),
  bboxAutoCalculate: z.literal(false).default(false),
  nativeBoundingBox: BoundingBoxPayloadSchema,
  latLonBoundingBox: BoundingBoxPayloadSchema,
})

const boundingBoxCoord = z
  .string()
  .refine(v => v !== '', { message: 'common.errors.required' })
  .refine(v => Number.isFinite(Number(v)), { message: 'common.errors.invalidNumber' })

export const BoundingBoxSchema = z.object({
  minX: boundingBoxCoord,
  minY: boundingBoxCoord,
  maxX: boundingBoxCoord,
  maxY: boundingBoxCoord,
  crs: z.string(),
})

export const LayerFormSchema = z.object({
  title: z.string().trim().min(1, 'common.errors.required'),
  layerName: z.string().trim().min(1, 'common.errors.required'),
  layerDescription: z
    .string()
    .trim()
    .max(NAMED_API_DESCRIPTION_MAX_LENGTH, 'datasets.overview.completion.apis.config.errors.description.tooLong')
    .optional()
    .or(z.literal('')),
  table: z.string().min(1, 'common.errors.required'),
  attribute: z.array(z.string()).min(1, 'common.errors.required'),
  cqlFilter: z.string().trim(),
  geometryColumnRef: z.string().min(1, 'common.errors.required'),
  crs: z.string().min(1, 'common.errors.required'),
  bboxAutoCalculate: z.literal(false),
  nativeBoundingBox: BoundingBoxSchema,
  latLonBoundingBox: BoundingBoxSchema,
  defaultStyleId: z.string().nullable(),
  alternativeStyleIds: z.array(z.string()),
})

export type Layer = z.infer<typeof LayerSchema>
export type LayerFormData = z.infer<typeof LayerFormSchema>
export type LayerApiPayload = z.infer<typeof LayerPayloadSchema>

export type CreateLayerInput = {
  datasetId: string
  data: LayerApiPayload
}

export type UpdateLayerInput = {
  datasetId: string
  layerId: string
  data: LayerApiPayload
}

// ============================================================================
// Types for WFS/WMS-API
// ============================================================================

export const WfsWmsApiFormSchema = ({ existingSlugs }: BuildSchemaArgs) =>
  z.object({
    type: z.literal(API_TYPE_QUERY.WFS_WMS),
    baseInfo: NamedApiBaseInfoFormSchema({ existingSlugs }),
    layer: LayerFormSchema,
  })

export type WfsWmsApiFormData = z.infer<ReturnType<typeof WfsWmsApiFormSchema>>

export const WfsWmsApiPayloadSchema = z.object({
  baseInfo: NamedApiPayloadSchema,
  layer: LayerPayloadSchema,
})
export type WfsWmsApiApiPayloadData = z.infer<typeof WfsWmsApiPayloadSchema>
