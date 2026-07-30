import { z } from 'zod'

import { LayerFormSchema } from './layers'
import { StyleFormSchema } from './styles'

export const SLUG_MAX_LENGTH = 32
export const NAMED_API_DESCRIPTION_MAX_LENGTH = 150

// Mirrors backend NamedApiInputDTO @Pattern: lowercase alphanumeric, internal hyphens only,
// no leading/trailing dash. Single-character slugs allowed.
export const SLUG_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/

// Slugs that would collide with reserved platform path segments (e.g. the management endpoint
// /datasets/{id}/apis and the /v1 data-plane prefix). Mirrors the backend blocklist —
// NamedApiAllowedSlugValidator.RESERVED — which is the authoritative source; keep the two in sync.
export const RESERVED_SLUGS = ['apis', 'api', 'v1', 'admin'] as const

// Public data-plane path prefix for a dataset's named APIs. The gateway serves them at
// /v1/datasets/{datasetId}/{slug} (issue #1368). Single source of truth so the scheme
// can't drift across the UI (URL preview, copy-to-clipboard); the caller appends the slug.
export const namedApiPathPrefix = (datasetId: string) => `/v1/datasets/${datasetId}/`

// Standard values mirror the backend enum names. UI labels are handled via translations.
export const API_STANDARDS = {
  OWS: 'OWS',
  STA: 'STA',
  CUSTOM: 'CUSTOM',
} as const

export type ApiStandard = (typeof API_STANDARDS)[keyof typeof API_STANDARDS]

export const API_TYPE_QUERY = {
  SENSORTHINGS: 'sensorthings',
  OWS: 'ows',
} as const

export type ApiTypeQuery = (typeof API_TYPE_QUERY)[keyof typeof API_TYPE_QUERY]

export const isApiTypeQuery = (value: unknown): value is ApiTypeQuery =>
  value === API_TYPE_QUERY.SENSORTHINGS || value === API_TYPE_QUERY.OWS

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
  [API_TYPE_QUERY.OWS]: {
    standard: API_STANDARDS.OWS,
    defaultSlug: 'ows',
    persistenceValue: PERSISTENCE_OPTIONS.POSTGIS.value,
    persistenceLabel: PERSISTENCE_OPTIONS.POSTGIS.label,
  },
}

export const NamedApiSchema = z.object({
  id: z.string().optional(),
  name: z.string(),
  slug: z.string(),
  standard: z.enum([API_STANDARDS.OWS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
  version: z.string().optional(),
  description: z.string().optional(),
  previewUrl: z.string().optional(),
})

export type NamedApi = z.infer<typeof NamedApiSchema>

export const NamedApiPayloadSchema = z.object({
  name: z.string(),
  slug: z.string(),
  standard: z.enum([API_STANDARDS.OWS, API_STANDARDS.STA, API_STANDARDS.CUSTOM]),
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

// ============================================================================
// Types for OWS-API
// ============================================================================

export const OwsApiFormSchema = ({ existingSlugs }: BuildSchemaArgs) =>
  z.object({
    type: z.literal(API_TYPE_QUERY.OWS),
    baseInfo: NamedApiBaseInfoFormSchema({ existingSlugs }),
    // Layer names are unique per dataset, not per sink — a duplicate would silently overwrite its
    // twin in GeoServer. The backend and a DB constraint are the authority; this only keeps it out
    // of the form.
    layers: z.array(LayerFormSchema).superRefine((layers, ctx) => {
      const seen = new Set<string>()
      layers.forEach((layer, index) => {
        const name = layer.layerName.trim().toLowerCase()
        if (!name) return
        if (seen.has(name)) {
          ctx.addIssue({
            code: 'custom',
            message: 'datasets.overview.completion.apis.config.errors.layerName.notUnique',
            path: [index, 'layerName'],
          })
          return
        }
        seen.add(name)
      })
    }),
    styles: z.array(StyleFormSchema),
  })

export type OwsApiFormData = z.infer<ReturnType<typeof OwsApiFormSchema>>
