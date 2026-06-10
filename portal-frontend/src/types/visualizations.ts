import { z } from 'zod'

export const VISUALIZATION_TYPE_QUERY = {
  DASHBOARD: 'dashboard',
} as const

export type VisualizationTypeQuery = (typeof VISUALIZATION_TYPE_QUERY)[keyof typeof VISUALIZATION_TYPE_QUERY]

export const isVisualizationTypeQuery = (value: unknown): value is VisualizationTypeQuery =>
  value === VISUALIZATION_TYPE_QUERY.DASHBOARD

export const VISUALIZATION_DESCRIPTION_MAX_LENGTH = 150

export const visualizationFormSchema = z.object({
  name: z.string().min(1),
  description: z.string().max(VISUALIZATION_DESCRIPTION_MAX_LENGTH).optional(),
})

export type VisualizationFormData = z.infer<typeof visualizationFormSchema>
