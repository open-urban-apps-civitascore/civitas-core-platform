import { useCreateMutation } from '@/hooks/use-create-mutation copy'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { CreateDatasetData, Dataset, PatchDatasetData, UpdateDatasetData } from '@/types/datasets'

const key = 'datasets'

export const useGetDatasets = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Dataset[]>({ key, params, isEnabled, errorMessage: 'An error occurred while loading datasets.' })

export const useCreateDataset = () =>
  useCreateMutation<Dataset, CreateDatasetData>({ key, errorMessage: 'An error occured while creating dataset' })

export const useUpdateDataset = () =>
  useUpdateMutation<Dataset, UpdateDatasetData>({
    method: 'PUT',
    key,
    errorMessage: 'An error occured while creating dataset',
  })

export const usePatchDataset = () =>
  useUpdateMutation<Dataset, PatchDatasetData>({
    method: 'PATCH',
    key,
    errorMessage: 'An error occured while creating dataset',
  })
