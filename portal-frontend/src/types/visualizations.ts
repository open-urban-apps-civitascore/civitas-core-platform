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
  createdAt: z.string(),
  modifiedAt: z.string(),
})

export type VisualizationApiResponse = z.infer<typeof VisualizationApiResponseSchema>

export const toVisualization = (dto: VisualizationApiResponse): Visualization => ({
  id: dto.id,
  name: dto.name,
  description: dto.description ?? undefined,
  type: VISUALIZATION_TYPE_QUERY.DASHBOARD,
})

export const VISUALIZATION_DESCRIPTION_MAX_LENGTH = 150

export const visualizationFormSchema = z.object({
  name: z.string().min(1),
  description: z.string().max(VISUALIZATION_DESCRIPTION_MAX_LENGTH).optional(),
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
  visualization: VisualizationFormData
}
