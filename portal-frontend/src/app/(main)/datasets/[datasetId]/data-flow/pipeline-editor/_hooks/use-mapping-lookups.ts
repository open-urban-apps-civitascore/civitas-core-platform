'use client'

/**
 * useMappingLookups Hook
 *
 * What the editor needs to draw the mapping nodes of a pipeline that has no stored graph: the stored
 * mapping documents, and the data structures to find the source and the target of each mapping.
 * The editor reads nothing when no such mapping node exists.
 */

import { useMemo } from 'react'

import { useGetDatastructures } from '@/app/services/api/datastructures/clientRequests'
import { type MappingDocument, useGetMappingDocuments } from '@/app/services/api/mappings/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { type HydrationDataStructure, mappingRefsToHydrate } from '../_services/modelHydrationService'
import type { PipelineOutputDTO } from '../_types/pipeline'

/**
 * One large page: a structure behind the first page would leave its mapping node closed, as if the
 * user could not read the structure.
 */
const STRUCTURE_LIST_PARAMS = new URLSearchParams({ size: '200' })

export interface MappingLookups {
  mappings: ReadonlyMap<string, MappingDocument>
  dataStructures: readonly HydrationDataStructure[]
  isLoading: boolean
}

export const useMappingLookups = (
  datasetId: string,
  pipelineDTOs: readonly PipelineOutputDTO[] | undefined,
): MappingLookups => {
  const { hasPermission } = usePermissions()
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  const references = useMemo(() => mappingRefsToHydrate(pipelineDTOs ?? []), [pipelineDTOs])
  const documents = useGetMappingDocuments(datasetId, references)
  const structuresQuery = useGetDatastructures({
    params: STRUCTURE_LIST_PARAMS,
    isEnabled: canReadDatastructures && references.length > 0,
  })

  const dataStructures = useMemo(() => structuresQuery.data?.data ?? [], [structuresQuery.data])

  return {
    mappings: documents.byReference,
    dataStructures,
    isLoading: documents.isLoading || structuresQuery.isLoading,
  }
}
