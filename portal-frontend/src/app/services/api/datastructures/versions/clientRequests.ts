import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import {
  DatastructureVersionCreateData,
  DatastructureVersionPatchData,
  DatastructureVersionSummary,
} from '@/types/datastructures'

const key = 'datastructureVersions'

// TODO: use renamed DatastructureCreateApiData when API is connected
export const useCreateDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersionSummary, DatastructureVersionCreateData>({
    key,
    endpoint: `/datastructures${datastructureId}/versions`,
    errorMessage: 'An error occurred while creating datastructure version',
  })

export const useUpdateDatastructureVersion = (datastructureId: string) =>
  useUpdateMutation<DatastructureVersionSummary, DatastructureVersionPatchData>({
    method: 'PATCH',
    key,
    endpoint: data => `/datastructures${datastructureId}/versions/${data.id}`,
    errorMessage: 'An error occurred while updating datastructure version',
  })
