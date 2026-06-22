import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { Datapool, DatapoolCreateData, DatapoolPatchData } from '@/types/datapools'

const key = 'datapools'

export const useGetDatapools = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Datapool[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datapools.',
  })

export const useCreateDatapool = () =>
  useCreateMutation<Datapool, DatapoolCreateData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occured while creating datapool',
  })

export const usePatchDatapool = () =>
  useUpdateMutation<Datapool, DatapoolPatchData>({
    method: 'PATCH',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occured while updating datapool',
  })

export const useDeleteDatapool = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting the datapool.',
  })
