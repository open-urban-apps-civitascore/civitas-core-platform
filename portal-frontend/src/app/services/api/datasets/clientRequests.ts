import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { Dataset, DatasetCreateApiData, DatasetUpdateApiData } from '@/types/datasets'

const key = 'datasets'

export const useGetDatasets = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Dataset[]>({ key, params, isEnabled, errorMessage: 'An error occurred while loading datasets.' })

export const useCreateDataset = () =>
  useCreateMutation<Dataset, DatasetCreateApiData>({ key, errorMessage: 'An error occured while creating dataset' })

export const useUpdateDataset = () =>
  useUpdateMutation<Dataset, DatasetUpdateApiData>({
    method: 'PUT',
    key,
    errorMessage: 'An error occured while updating dataset',
  })

export const usePatchDataset = () =>
  useUpdateMutation<Dataset, DatasetUpdateApiData>({
    method: 'PATCH',
    key,
    errorMessage: 'An error occured while updating dataset',
  })
