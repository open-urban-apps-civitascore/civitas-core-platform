/**
 * Mock Entity Data for Pipeline Editor
 *
 * Contains mock data for API entities until real APIs exist.
 * DataSources use the real API via useGetDatasources().
 *
 */

import type { MockApiEntity } from '../_types/nodes'

// ============================================================================
// Mock API Entities
// ============================================================================

/**
 * Mock API entities for dropdown selection.
 * Used in ApiRequest and ApiResponse nodes.
 *
 */
export const MOCK_API_ENTITIES: MockApiEntity[] = [
  {
    id: 'api-1',
    name: 'Weather Data API',
    method: 'GET',
    path: '/api/v1/weather',
    description: 'Fetches current weather data for a given location',
  },
  {
    id: 'api-2',
    name: 'Traffic Data API',
    method: 'POST',
    path: '/api/v1/traffic/analyze',
    description: 'Submits traffic data for analysis',
  },
]
