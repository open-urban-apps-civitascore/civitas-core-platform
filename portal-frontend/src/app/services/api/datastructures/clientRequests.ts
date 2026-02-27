import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetItemInput } from '@/types/common'
import { Datastructure, DatastructureCreateFormData, DatastructurePatchData } from '@/types/datastructures'

const key = 'datastructures'

export const useGetDatastructure = ({ id }: GetItemInput) =>
  useDataQuery<Datastructure>({
    key,
    id,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datastructures.',
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
