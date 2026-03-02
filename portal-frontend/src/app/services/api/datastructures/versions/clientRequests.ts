import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import {
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionPatchData,
  DatastructureVersionPutData,
} from '@/types/datastructures'

export const useCreateDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, DatastructureVersionCreateData>({
    key: `datastructures/${datastructureId}/versions`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating datastructure version',
  })

export const useUpdateDatastructureVersion = (datastructureId: string) =>
  useUpdateMutation<DatastructureVersion, DatastructureVersionPatchData>({
    method: 'PATCH',
    key: `datastructures/${datastructureId}/versions`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure version',
  })

export const useUpdateDatastructureVersionPublished = (datastructureId: string) =>
  useUpdateMutation<DatastructureVersion, DatastructureVersionPutData>({
    method: 'PUT',
    key: `datastructures/${datastructureId}/versions`,
    endpoint: (versionId: string) => `datastructures/${datastructureId}/versions/${versionId}/published/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure version',
  })

export const usePublishDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, void>({
    key: `datastructures/${datastructureId}/versions`,
    endpoint: (versionId: string) => `datastructures/${datastructureId}/versions/${versionId}/publish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure version',
  })

export const useUnpublishDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, void>({
    key: `datastructures/${datastructureId}/versions`,
    endpoint: (versionId: string) => `datastructures/${datastructureId}/versions/${versionId}/unpublish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure version',
  })
