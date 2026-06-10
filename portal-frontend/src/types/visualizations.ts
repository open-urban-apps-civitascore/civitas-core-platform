export const VISUALIZATION_TYPE_QUERY = {
  DASHBOARD: 'dashboard',
} as const

export type VisualizationTypeQuery = (typeof VISUALIZATION_TYPE_QUERY)[keyof typeof VISUALIZATION_TYPE_QUERY]

export const isVisualizationTypeQuery = (value: unknown): value is VisualizationTypeQuery =>
  value === VISUALIZATION_TYPE_QUERY.DASHBOARD
