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
import { DATASET_STATUS_TYPES } from '@/types/datasets'

export interface PipelinePermissions {
  canDeletePipeline: boolean
  canReadDatastructures: boolean
  canCreatePipeline: boolean
  canEditPipeline: boolean
}

export const usePipelinePermissions = (datasetId: string, datapoolId?: string): PipelinePermissions => {
  const { hasPermission, hasScopedPermission } = usePermissions()
  const { data: dataset } = useGetDataset({ id: datasetId })
  const resolvedDatapoolId = datapoolId ?? dataset?.data?.datapool?.id ?? undefined

  const datasetStatus = dataset?.data.dataSetStatus
  const isDraftMode = datasetStatus === DATASET_STATUS_TYPES.DRAFT

  const scoped = (permission: PermissionName) =>
    hasScopedPermission(permission, ASSIGNMENT_SCOPE_TYPES.DATASET, datasetId, resolvedDatapoolId)

  const canReadDataset = scoped(PERMISSION_NAMES.DATASET_READ)
  const canCreateDataset = scoped(PERMISSION_NAMES.DATASET_CREATE) && isDraftMode
  const canUpdateDataset = scoped(PERMISSION_NAMES.DATASET_UPDATE) && isDraftMode
  const canDeletePipeline = scoped(PERMISSION_NAMES.DATASET_DELETE) && isDraftMode
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  return {
    canDeletePipeline,
    canReadDatastructures,
    canCreatePipeline: canReadDataset && canCreateDataset && canUpdateDataset && canReadDatastructures,
    canEditPipeline: canReadDataset && canUpdateDataset && canReadDatastructures,
  }
}
