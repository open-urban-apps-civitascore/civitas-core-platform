import { useMutation } from '@tanstack/react-query'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import type { MappingField } from '@/generated/core'

/**
 * API client for CORE Mapping artifacts.
 *
 * Mappings are nested under the dataset that holds them. The `/api` proxy prepends the backend's
 * `/v1` base, so the endpoint here is `/datasets/{datasetId}/mappings` (the backend route is
 * `POST|PUT /v1/datasets/{dataSetId}/mappings`).
 *
 * - POST creates a brand-new logical Mapping (first version).
 * - PUT versions an existing logical Mapping (identified by its `logicalUrn`).
 *
 * Both return the logical URN and the freshly minted versioned URN; the pipeline node references the
 * mapping by its `versionedUrn`.
 */

const mappingsEndpoint = (datasetId: string) => `/datasets/${datasetId}/mappings`
const API_REQUEST_HEADER = 'x-api-request'

/** The CORE Mapping document body sent to the backend (no `$schema`/`id` — Model Forge stamps those). */
export interface MappingArtifactBody {
  /** Versioned CORE URN of the source DataStructure. */
  source?: string
  /** Versioned CORE URN of the target DataStructure. */
  target?: string
  /** Map of target field paths to their field operations. */
  fields: Record<string, MappingField>
  /** Human-readable display name, e.g. `${sourceName}-to-${targetName}`. */
  title: string
  /** Editor-only node positions, round-tripped for the mapping editor. */
  positions?: Record<string, { x: number; y: number }>
}

export type CreateMappingInput = MappingArtifactBody

export interface UpdateMappingInput extends MappingArtifactBody {
  /** Logical URN of the mapping to version. */
  logicalUrn: string
}

/** Response of `POST|PUT /v1/datasets/{dataSetId}/mappings`. */
export interface MappingArtifactResponse {
  logicalUrn: string
  versionedUrn: string
}

/**
 * Creates a new Mapping artifact under a dataset.
 * POST /v1/datasets/{datasetId}/mappings
 */
export const useCreateMapping = (datasetId: string) =>
  useMutation<ApiServiceResponse<MappingArtifactResponse>, unknown, CreateMappingInput>({
    mutationFn: (data: CreateMappingInput) =>
      apiRequest<MappingArtifactResponse>({
        method: 'POST',
        endpoint: mappingsEndpoint(datasetId),
        headers: { [API_REQUEST_HEADER]: 'true' },
        data,
        errorMessage: 'An error occurred while creating the mapping.',
      }),
  })

/**
 * Versions an existing Mapping artifact of a dataset.
 * PUT /v1/datasets/{datasetId}/mappings
 */
export const useUpdateMapping = (datasetId: string) =>
  useMutation<ApiServiceResponse<MappingArtifactResponse>, unknown, UpdateMappingInput>({
    mutationFn: (data: UpdateMappingInput) =>
      apiRequest<MappingArtifactResponse>({
        method: 'PUT',
        endpoint: mappingsEndpoint(datasetId),
        headers: { [API_REQUEST_HEADER]: 'true' },
        data,
        errorMessage: 'An error occurred while updating the mapping.',
      }),
  })
