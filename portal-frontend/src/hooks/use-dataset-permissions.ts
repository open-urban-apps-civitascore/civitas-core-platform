'use client'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES, type PermissionName } from '@/types/currentUser'
import { Dataset, DATASET_STATUS_TYPES } from '@/types/datasets'

export type DatasetPermissionSubject = Pick<Dataset, 'id' | 'dataSetStatus' | 'datapool'>

export interface DatasetPermissions {
  isDraft: boolean
  canRead: boolean
  canRelease: boolean
  canReadDatastructures: boolean
  canEditMetadata: boolean
  canViewApis: boolean
  canEditApis: boolean
  canEditPipeline: boolean
  canCreatePipeline: boolean
  canDeletePipeline: boolean
}

// Single source of truth for what may be done with a dataset and the apis and pipelines inside it.
// Fails closed while the dataset or the permissions are still loading.
export const useDatasetPermissions = (dataset: DatasetPermissionSubject | undefined): DatasetPermissions => {
  const { hasPermission, hasScopedPermission } = usePermissions()

  const isDraft = dataset?.dataSetStatus === DATASET_STATUS_TYPES.DRAFT
  const isAvailable = dataset?.dataSetStatus === DATASET_STATUS_TYPES.AVAILABLE

  const scoped = (permission: PermissionName) =>
    !!dataset &&
    hasScopedPermission(permission, ASSIGNMENT_SCOPE_TYPES.DATASET, dataset.id, dataset.datapool?.id ?? undefined)

  const canRead = scoped(PERMISSION_NAMES.DATASET_READ)
  const canCreate = scoped(PERMISSION_NAMES.DATASET_CREATE)
  const canUpdate = scoped(PERMISSION_NAMES.DATASET_UPDATE)
  const canDelete = scoped(PERMISSION_NAMES.DATASET_DELETE)
  const canRelease = scoped(PERMISSION_NAMES.DATASET_RELEASE)
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  // Metadata stays editable after release. Updating the released dataset needs DATASET_RELEASE.
  const canEditMetadata = canUpdate && (!isAvailable || canRelease)

  // The api layer config additionally needs data structures permissions
  const canViewApis = canRead && canReadDatastructures
  const canEditApis = canViewApis && canUpdate && isDraft

  // Editing a pipeline additionally requires data structures and datasources permissions
  const canEditPipeline = canRead && canUpdate && isDraft && canReadDatasources && canReadDatastructures
  const canCreatePipeline = canEditPipeline && canCreate
  const canDeletePipeline = canDelete && isDraft

  return {
    isDraft,
    canRead,
    canRelease,
    canReadDatastructures,
    canEditMetadata,
    canViewApis,
    canEditApis,
    canEditPipeline,
    canCreatePipeline,
    canDeletePipeline,
  }
}

export const useDatasetPermissionsById = (datasetId: string): DatasetPermissions & { isLoading: boolean } => {
  const { data, isPending } = useGetDataset({ id: datasetId })
  return { ...useDatasetPermissions(data?.data), isLoading: isPending }
}
