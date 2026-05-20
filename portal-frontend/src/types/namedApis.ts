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

export const NamedApiInputSchema = z.object({
  name: z.string(),
  slug: z.string(),
  standard: z.enum([API_STANDARDS.WFS, API_STANDARDS.WMS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
  version: z.string().optional(),
  description: z.string().optional(),
})

export type NamedApiInput = z.infer<typeof NamedApiInputSchema>

interface BuildSchemaArgs {
  existingSlugs: string[]
}

export const buildNamedApiFormSchema = ({ existingSlugs }: BuildSchemaArgs) => {
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

const boundingBoxCoord = z.string().transform((v, ctx) => {
  if (v === '') {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'common.errors.required' })
    return z.NEVER
  }
  const n = Number(v)
  if (!Number.isFinite(n)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'common.errors.invalidNumber' })
    return z.NEVER
  }
  return n
})

export const BoundingBoxSchema = z.object({
  minX: boundingBoxCoord,
  minY: boundingBoxCoord,
  maxX: boundingBoxCoord,
  maxY: boundingBoxCoord,
  crs: z.string(),
})

export const WfsWmsLayerFormSchema = z.object({
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
  bboxAutoCalculate: z.boolean(),
  nativeBoundingBox: BoundingBoxSchema,
  latLonBoundingBox: BoundingBoxSchema,
  defaultStilId: z.string().optional(),
  alternativeStilIds: z.string().optional(),
})

export const WfsWmsApiFormSchema = ({ existingSlugs }: BuildSchemaArgs) =>
  buildNamedApiFormSchema({ existingSlugs }).extend({ layer: WfsWmsLayerFormSchema })

export type NamedApiFormData = z.input<ReturnType<typeof buildNamedApiFormSchema>>
export type WfsWmsApiFormData = z.input<ReturnType<typeof WfsWmsApiFormSchema>>
