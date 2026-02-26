import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import {
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionPatchData,
} from '@/types/datastructures'

export const useCreateDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, DatastructureVersionCreateData>({
    key: `datastructures${datastructureId}/versions`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while creating datastructure version',
  })

export const useUpdateDatastructureVersion = (datastructureId: string) =>
  useUpdateMutation<DatastructureVersion, DatastructureVersionPatchData>({
    method: 'PATCH',
    key: `datastructures${datastructureId}/versions`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while updating datastructure version',
  })
