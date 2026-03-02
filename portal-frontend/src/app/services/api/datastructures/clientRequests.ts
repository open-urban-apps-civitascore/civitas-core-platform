import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { Datastructure, DatastructureCreateFormData, DatastructurePatchData } from '@/types/datastructures'

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
