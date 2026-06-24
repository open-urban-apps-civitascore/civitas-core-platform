'use client'

/**
 * usePipelinePermissions Hook
 *
 * Resolves the dataset-scoped permissions relevant to the pipeline editor.
 * Encapsulates the DATASET scope + datapool cascade in one place so call sites
 * only read the resulting flags instead of repeating scope ids.
 */

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES, type PermissionName } from '@/types/currentUser'

export interface PipelinePermissions {
  canCreate: boolean
  canUpdate: boolean
  canDelete: boolean
  canRelease: boolean
  canReadDatasources: boolean
  canReadDatastructures: boolean
  /** Editing a pipeline requires create, update, datasource-read and datastructure-read permissions together. */
  canEdit: boolean
}

export const usePipelinePermissions = (datasetId: string): PipelinePermissions => {
  const { hasPermission, hasScopedPermission } = usePermissions()
  const { data } = useGetDataset({ id: datasetId })
  const datapoolId = data?.data?.datapool?.id ?? undefined

  const scoped = (permission: PermissionName) =>
    hasScopedPermission(permission, ASSIGNMENT_SCOPE_TYPES.DATASET, datasetId, datapoolId)

  const canCreate = scoped(PERMISSION_NAMES.DATASET_CREATE)
  const canUpdate = scoped(PERMISSION_NAMES.DATASET_UPDATE)
  const canDelete = scoped(PERMISSION_NAMES.DATASET_DELETE)
  const canRelease = scoped(PERMISSION_NAMES.DATASET_RELEASE)
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  return {
    canCreate,
    canUpdate,
    canDelete,
    canRelease,
    canReadDatasources,
    canReadDatastructures,
    canEdit: canCreate && canUpdate && canReadDatasources && canReadDatastructures,
  }
}
