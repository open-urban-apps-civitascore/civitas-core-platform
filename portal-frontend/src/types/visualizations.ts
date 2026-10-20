import { z } from 'zod'

export const VISUALIZATION_TYPE_QUERY = {
  DASHBOARD: 'dashboard',
} as const

export type VisualizationTypeQuery = (typeof VISUALIZATION_TYPE_QUERY)[keyof typeof VISUALIZATION_TYPE_QUERY]

export const isVisualizationTypeQuery = (value: unknown): value is VisualizationTypeQuery =>
  value === VISUALIZATION_TYPE_QUERY.DASHBOARD

export interface Visualization {
  id: string
  name: string
  description?: string
  type: VisualizationTypeQuery
  // Null until the rendering engine (Superset) has materialized the dashboard.
  externalDashboardId: string | null
  released: boolean
  standaloneMode: 0 | 1 | 2 | 3
  showFilters: boolean
  filterExpansion: 'DEFAULT' | 'COLLAPSED' | 'EXPANDED'
}

// --- Backend schema (DashboardOutputDTO; visualizationType is the DTO's uppercase discriminator
// - the API currently only has one visualization kind, but the union is meant to grow)

export const VisualizationApiResponseSchema = z.object({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullable().optional(),
  visualizationType: z.literal('DASHBOARD'),
  dataSetId: z.uuid(),
  externalDashboardId: z.string().nullable(),
  active: z.boolean(),
  released: z.boolean(),
  standaloneMode: z.number().int().min(0).max(3),
  showFilters: z.boolean(),
  filterExpansion: z.enum(['DEFAULT', 'COLLAPSED', 'EXPANDED']),
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type VisualizationApiResponse = z.infer<typeof VisualizationApiResponseSchema>

export const toVisualization = (dto: VisualizationApiResponse): Visualization => ({
  id: dto.id,
  name: dto.name,
  description: dto.description ?? undefined,
  type: VISUALIZATION_TYPE_QUERY.DASHBOARD,
  externalDashboardId: dto.externalDashboardId,
  released: dto.released,
  standaloneMode: dto.standaloneMode as 0 | 1 | 2 | 3,
  showFilters: dto.showFilters,
  filterExpansion: dto.filterExpansion,
})

export const VISUALIZATION_DESCRIPTION_MAX_LENGTH = 150

export const visualizationFormSchema = z.object({
  name: z.string().min(1),
  description: z.string().max(VISUALIZATION_DESCRIPTION_MAX_LENGTH).optional(),
  standaloneMode: z.number().int().min(0).max(3),
  showFilters: z.boolean(),
  filterExpansion: z.enum(['DEFAULT', 'COLLAPSED', 'EXPANDED']),
})

export type VisualizationFormData = z.infer<typeof visualizationFormSchema>

export type CreateVisualizationInput = {
  datasetId: string
  visualization: VisualizationFormData
}

export type DeleteVisualizationInput = {
  datasetId: string
  visualizationId: string
}

export type UpdateVisualizationInput = {
  datasetId: string
  visualizationId: string
  visualization: VisualizationFormData & { released?: boolean }
}

// VisualizationInputDTO's @JsonTypeInfo discriminator lives on the abstract base class, so Jackson
// requires it on the wire even though the controller binds directly to the concrete
// DashboardInputDTO — the form itself never lets the user pick a type, so it's added here rather
// than carried around in VisualizationFormData.
export const toVisualizationPayload = (data: VisualizationFormData & { released?: boolean }) => ({
  ...data,
  visualizationType: 'DASHBOARD',
})
