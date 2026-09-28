'use client'

/**
 * useDatastructureVersionInfo Hook
 *
 * Resolves the datastructure name, version number and status for a GeoPersistence node from its stored
 * `dataStructureVersionId` composite key. The values are fetched by id at render time (cached by
 * react-query) rather than read from the node, and resolve to an anonymous label when the user
 * lacks DATASTRUCTURE_READ.
 */

import { useTranslations } from 'next-intl'

import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatastructureStatusType } from '@/types/datastructures'

/**
 * Parses the "datastructureId/versionId" composite key used by the datastructure assignment modal.
 */
export const parseCompositeKey = (key: string): { datastructureId: string; versionId: string } | null => {
  const parts = key.split('/')
  if (parts.length !== 2) return null
  return { datastructureId: parts[0], versionId: parts[1] }
}

export interface DatastructureVersionInfo {
  name: string | undefined
  versionNumber: string | undefined
  status: DatastructureStatusType | undefined
}

export const useDatastructureVersionInfo = (dataStructureVersionId: string | undefined): DatastructureVersionInfo => {
  const t = useTranslations('pipelineEditor')
  const { hasPermission } = usePermissions()
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  const parsed = dataStructureVersionId ? parseCompositeKey(dataStructureVersionId) : null

  const { data } = useGetDatastructureVersion({
    datastructureId: parsed?.datastructureId ?? '',
    versionId: parsed?.versionId ?? '',
    isEnabled: canReadDatastructures && parsed !== null,
  })

  if (parsed === null) return { name: undefined, versionNumber: undefined, status: undefined }
  if (!canReadDatastructures)
    return { name: t('geoPersistencePanel.anonymousDataStructure'), versionNumber: undefined, status: undefined }

  const version = data?.data
  return {
    name: version?.dataStructure?.name,
    versionNumber: version?.version ?? undefined,
    status: version?.dataStructureVersionStatus,
  }
}
