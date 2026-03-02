import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import {
  Datastructure,
  DatastructureCreateFormData,
  DatastructurePatchData,
  DatastructurePutData,
} from '@/types/datastructures'

const key = 'datastructures'

export const useCreateDatastructure = () =>
  useCreateMutation<Datastructure, DatastructureCreateFormData>({
    key,
    errorMessage: 'An error occurred while creating datastructure',
    headers: { 'x-api-request': 'true' },
  })

export const useUpdateDatastructure = () =>
  useUpdateMutation<Datastructure, DatastructurePatchData>({
    method: 'PATCH',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure',
  })

export const useUpdateDatastructurePublished = () =>
  useUpdateMutation<Datastructure, DatastructurePutData>({
    method: 'PUT',
    key,
    endpoint: (datastructureId: string) => `datastructures/${datastructureId}/published/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure',
  })

export const usePublishDatastructure = () =>
  useCreateMutation<Datastructure, void>({
    key,
    endpoint: (datastructureId: string) => `datastructures/${datastructureId}/publish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure version',
  })

export const useUnpublishDatastructure = () =>
  useCreateMutation<Datastructure, void>({
    key,
    endpoint: (datastructureId: string) => `datastructures/${datastructureId}/unpublish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure version',
  })
