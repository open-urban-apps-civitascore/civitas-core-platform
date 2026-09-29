import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput, WithId } from '@/types/common'
import {
  Datastructure,
  DatastructureCreateFormData,
  DatastructureMetaPatchData,
  DatastructurePatchData,
} from '@/types/datastructures'

const key = 'datastructures'

export const useGetDatastructures = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Datastructure[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching datastructures.',
  })

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

export const useUpdateDatastructureReleased = () =>
  useUpdateMutation<Datastructure, DatastructureMetaPatchData>({
    method: 'PATCH',
    key,
    endpoint: ({ id }) => `/datastructures/${id}/released/meta`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure',
  })

export const useReleaseDatastructure = () =>
  useCreateMutation<Datastructure, WithId>({
    key,
    endpoint: ({ id }) => `/datastructures/${id}/release`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while releasing datastructure',
  })

export const useUnreleaseDatastructure = () =>
  useCreateMutation<Datastructure, WithId>({
    key,
    endpoint: ({ id }) => `/datastructures/${id}/unrelease`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while unreleasing datastructure',
  })

export const useDeleteDatastructure = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting datastructure',
  })
