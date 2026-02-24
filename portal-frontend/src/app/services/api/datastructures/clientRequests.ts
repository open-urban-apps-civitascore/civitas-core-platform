import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { Datastructure, DatastructureCreateData, DatastructureUpdateData } from '@/types/datastructures'

const key = 'datastructures'

// TODO: use renamed DatastructureCreateApiData when API is connected
export const useCreateDatastructure = () =>
  useCreateMutation<Datastructure, DatastructureCreateData>({
    key,
    errorMessage: 'An error occurred while creating datastructure',
  })

export const useUpdateDatastructure = () =>
  useUpdateMutation<Datastructure, DatastructureUpdateData>({
    //TODO: switch to PUT method when API is connected
    method: 'PATCH',
    key,
    errorMessage: 'An error occurred while updating datastructure',
  })
