import { serverFetch } from '@/lib/serverFetch'
import { VisualizationApiResponse } from '@/types/visualizations'

/**
 * Server-side data fetching for visualizations
 * These functions use serverFetch which directly communicates with the backend
 * using NextAuth's auth() to get the access token.
 *
 * IMPORTANT: Only use these in Server Components and Server Actions
 * For client-side requests, use the clientRequests.ts functions instead
 */

export const getVisualization = async (datasetId: string, visualizationId: string) => {
  try {
    return await serverFetch<VisualizationApiResponse>({
      endpoint: `/datasets/${datasetId}/visualizations/${visualizationId}`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching visualization ${visualizationId}.`, error)
    throw new Error('An error occurred while fetching visualization.')
  }
}
