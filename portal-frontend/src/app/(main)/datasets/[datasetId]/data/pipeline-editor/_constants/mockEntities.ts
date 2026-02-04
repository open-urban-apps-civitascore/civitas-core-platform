/**
 * Mock Entity Data for Pipeline Editor
 *
 * Contains mock data for API and FROST entities until real APIs exist.
 * DataSources use the real API via useGetDatasources().
 *
 */

import type { MockApiEntity, MockFrostEntity } from '../_types/nodes'

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

// ============================================================================
// Mock FROST Entities
// ============================================================================

/**
 * Mock FROST/Persistence entities for dropdown selection.
 * Used in FROST storage nodes.
 *
 */
export const MOCK_FROST_ENTITIES: MockFrostEntity[] = [
  {
    id: 'frost-1',
    name: 'City Sensors FROST Server',
    serverUrl: 'https://frost.city-sensors.example.com/v1.1',
    version: '1.1',
    description: 'SensorThings API for city-wide sensor network',
  },
  {
    id: 'frost-2',
    name: 'Environmental Monitoring',
    serverUrl: 'https://env-monitoring.example.com/sta/v1.0',
    version: '1.0',
    description: 'Environmental data collection and storage',
  },
]
