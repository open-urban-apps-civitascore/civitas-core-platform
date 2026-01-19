import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput, GetListInput } from '@/types/common'
import { DataSpace, DataSpaceFormData, PatchDataspaceData, UpdateDataspaceData } from '@/types/dataspaces'

const key = 'dataspaces'

export const useGetDataspaces = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<DataSpace[]>({
    key,
    params,
    isEnabled,
    errorMessage: 'An error occurred while fetching data spaces.',
  })

export const useGetDataspace = ({ id, isEnabled }: GetItemInput) =>
  useDataQuery<DataSpace>({
    id,
    key,
    isEnabled,
    errorMessage: 'An error occurred while fetching the data space.',
  })

export const useCreateDataspace = () =>
  useCreateMutation<DataSpace, DataSpaceFormData>({
    key,
    errorMessage: 'An error occurred while creating the data space.',
  })

export const useUpdateDataspace = () =>
  useUpdateMutation<DataSpace, UpdateDataspaceData>({
    key,
    method: 'PUT',
    errorMessage: 'An error occurred while updating the data space.',
  })

export const usePatchDataspace = () =>
  useUpdateMutation<DataSpace, PatchDataspaceData>({
    key,
    method: 'PATCH',
    errorMessage: 'An error occurred while updating the data space.',
  })

export const useDeleteDataSpace = (id: string) =>
  useDeleteMutation({ id, key, errorMessage: 'An error occurred while deleting the data space.' })
