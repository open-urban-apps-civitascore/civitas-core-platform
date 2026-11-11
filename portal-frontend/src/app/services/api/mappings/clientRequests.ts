import { useMutation } from '@tanstack/react-query'

import { apiRequest, type ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import type { MappingField } from '@/generated/core'
import { idOfUrn } from '@/utils/coreUrn'

/**
 * API client for CORE Mapping artifacts.
 *
 * Mappings are nested under the dataset that holds them. The `/api` proxy prepends the backend's
 * `/v1` base, so the collection here is `/datasets/{datasetId}/mappings` and each mapping is the
 * item `/datasets/{datasetId}/mappings/{mappingId}`, where `mappingId` is the UUID of its CORE URN.
 *
 * - POST on the collection creates a brand-new logical Mapping (first version).
 * - PUT on the item stores a new version of the Mapping.
 * - DELETE on the item removes the logical Mapping with all its versions.
 *
 * POST and PUT return the logical URN and the freshly minted versioned URN; the pipeline node
 * references the mapping by its `versionedUrn`.
 */

const mappingsEndpoint = (datasetId: string) => `/datasets/${datasetId}/mappings`
const mappingEndpoint = (datasetId: string, logicalUrn: string) =>
  `${mappingsEndpoint(datasetId)}/${idOfUrn(logicalUrn)}`
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

/** Response of a mapping create or update. */
export interface MappingArtifactResponse {
  mappingId: string
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
 * Stores a new version of a Mapping artifact of a dataset.
 * PUT /v1/datasets/{datasetId}/mappings/{mappingId}
 */
export const useUpdateMapping = (datasetId: string) =>
  useMutation<ApiServiceResponse<MappingArtifactResponse>, unknown, UpdateMappingInput>({
    mutationFn: ({ logicalUrn, ...body }: UpdateMappingInput) =>
      apiRequest<MappingArtifactResponse>({
        method: 'PUT',
        endpoint: mappingEndpoint(datasetId, logicalUrn),
        headers: { [API_REQUEST_HEADER]: 'true' },
        data: body,
        errorMessage: 'An error occurred while updating the mapping.',
      }),
  })

/**
 * Deletes a Mapping artifact of a dataset, given its logical CORE URN.
 * DELETE /v1/datasets/{datasetId}/mappings/{mappingId}
 */
export const useDeleteMapping = (datasetId: string) =>
  useMutation<ApiServiceResponse<void>, unknown, string>({
    mutationFn: (logicalUrn: string) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: mappingEndpoint(datasetId, logicalUrn),
        headers: { [API_REQUEST_HEADER]: 'true' },
        errorMessage: 'An error occurred while deleting the mapping.',
      }),
  })
