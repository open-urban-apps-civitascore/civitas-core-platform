import { useMutation, useQueries, type UseQueryResult } from '@tanstack/react-query'

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
 * - DELETE removes a logical Mapping, addressed by URN in the query string.
 *
 * POST and PUT return the logical URN and the freshly minted versioned URN; the pipeline node
 * references the mapping by its `versionedUrn`.
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

/** A stored mapping document as the backend returns it: its rules, and the editor's node layout. */
export type MappingDocument = Record<string, unknown>

interface StoredMapping {
  /** The URN the document was read by. */
  reference: string
  document: MappingDocument
}

export interface MappingDocuments {
  /** The documents that could be read, by the URN they were read by. */
  byReference: ReadonlyMap<string, MappingDocument>
  isLoading: boolean
}

/** Module-level, so that react-query keeps the combined result while the queries do not change. */
const combineMappingDocuments = (results: UseQueryResult<StoredMapping>[]): MappingDocuments => {
  const byReference = new Map<string, MappingDocument>()
  for (const result of results) {
    if (result.data) byReference.set(result.data.reference, result.data.document)
  }
  return { byReference, isLoading: results.some(result => result.isLoading) }
}

/**
 * Reads stored Mapping documents of a dataset by their CORE URNs, one request per URN.
 * GET /v1/datasets/{datasetId}/mappings?urn={urn}
 *
 * A document that cannot be read (deleted, or not a member of the dataset) is left out of the
 * result. The caller then works without it, so the request is not repeated.
 */
export const useGetMappingDocuments = (datasetId: string, urns: readonly string[]): MappingDocuments =>
  useQueries({
    queries: urns.map(urn => ({
      queryKey: [mappingsEndpoint(datasetId), urn],
      queryFn: async (): Promise<StoredMapping> => {
        const response = await apiRequest<MappingDocument>({
          method: 'GET',
          endpoint: mappingsEndpoint(datasetId),
          params: new URLSearchParams({ urn }),
          headers: { [API_REQUEST_HEADER]: 'true' },
          errorMessage: 'An error occurred while fetching the mapping.',
        })
        return { reference: urn, document: response.data }
      },
      enabled: Boolean(datasetId),
      retry: false,
    })),
    combine: combineMappingDocuments,
  })

/**
 * Deletes a Mapping artifact of a dataset by its logical CORE URN.
 * DELETE /v1/datasets/{datasetId}/mappings?urn={logicalUrn}
 */
export const useDeleteMapping = (datasetId: string) =>
  useMutation<ApiServiceResponse<void>, unknown, string>({
    mutationFn: (logicalUrn: string) =>
      apiRequest<void>({
        method: 'DELETE',
        endpoint: mappingsEndpoint(datasetId),
        params: new URLSearchParams({ urn: logicalUrn }),
        headers: { [API_REQUEST_HEADER]: 'true' },
        errorMessage: 'An error occurred while deleting the mapping.',
      }),
  })
