import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { Datastructure, DatastructureCreateJsonServerData, DatastructureUpdateData } from '@/types/datastructures'

const key = 'datastructures'

// TODO: use renamed DatastructureCreateApiData when API is connected
export const useCreateDatastructure = () =>
  useCreateMutation<Datastructure, DatastructureCreateJsonServerData>({
    key,
    errorMessage: 'An error occurred while creating datastructure',
  })

export const useUpdateDatastructure = () =>
  useUpdateMutation<Datastructure, DatastructureUpdateData>({
    method: 'PUT',
    key,
    errorMessage: 'An error occurred while updating datastructure',
  })
