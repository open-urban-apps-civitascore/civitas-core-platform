import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { WithId } from '@/types/common'
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
    endpoint: ({ id }) => `datastructures/${id}/published/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure',
  })

export const usePublishDatastructure = () =>
  useCreateMutation<Datastructure, WithId>({
    key,
    endpoint: ({ id }) => `datastructures/${id}/publish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure',
  })

export const useUnpublishDatastructure = () =>
  useCreateMutation<Datastructure, WithId>({
    key,
    endpoint: ({ id }) => `datastructures/${id}/unpublish`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while publishing datastructure',
  })

export const useDeleteDatastructure = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting datastructure',
  })
