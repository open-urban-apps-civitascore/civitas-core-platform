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
  canDeletePipeline: boolean
  canReadDatasources: boolean
  canReadDatastructures: boolean
  canCreatePipeline: boolean
  canEditPipeline: boolean
}

export const usePipelinePermissions = (datasetId: string): PipelinePermissions => {
  const { hasPermission, hasScopedPermission } = usePermissions()
  const { data } = useGetDataset({ id: datasetId })
  const datapoolId = data?.data?.datapool?.id ?? undefined

  const scoped = (permission: PermissionName) =>
    hasScopedPermission(permission, ASSIGNMENT_SCOPE_TYPES.DATASET, datasetId, datapoolId)

  const canCreateDataset = scoped(PERMISSION_NAMES.DATASET_CREATE)
  const canUpdateDataset = scoped(PERMISSION_NAMES.DATASET_UPDATE)
  const canDeletePipeline = scoped(PERMISSION_NAMES.DATASET_DELETE)
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  return {
    canDeletePipeline,
    canReadDatasources,
    canReadDatastructures,
    canCreatePipeline: canCreateDataset && canUpdateDataset && canReadDatasources && canReadDatastructures,
    canEditPipeline: canUpdateDataset && canReadDatasources && canReadDatastructures,
  }
}
