import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import {
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionPatchData,
} from '@/types/datastructures'

const key = 'datastructureVersions'

// TODO: use renamed DatastructureCreateApiData when API is connected
export const useCreateDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, DatastructureVersionCreateData>({
    key,
    endpoint: `/datastructures${datastructureId}/versions`,
    errorMessage: 'An error occurred while creating datastructure version',
  })

export const useUpdateDatastructureVersion = (datastructureId: string) =>
  useUpdateMutation<DatastructureVersion, DatastructureVersionPatchData>({
    method: 'PATCH',
    key,
    endpoint: data => `/datastructures${datastructureId}/versions/${data.id}`,
    errorMessage: 'An error occurred while updating datastructure version',
  })
