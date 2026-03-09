import { useMutation, useQueryClient } from '@tanstack/react-query'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { GetListInput } from '@/types/common'
import { Dataset, DatasetCreateApiData, DatasetPatchApiData, DatasetUpdateApiData } from '@/types/datasets'

const key = 'datasets'

export const useGetDatasets = ({ params, isEnabled }: GetListInput = {}) =>
  useDataQuery<Dataset[]>({
    key,
    params,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while loading datasets.',
  })

export const useCreateDataset = () =>
  useCreateMutation<Dataset, DatasetCreateApiData>({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occured while creating dataset',
  })

export const useUpdateDataset = () =>
  useUpdateMutation<Dataset, DatasetUpdateApiData>({
    method: 'PUT',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occured while updating dataset',
  })

export const usePatchDataset = () =>
  useUpdateMutation<Dataset, DatasetPatchApiData>({
    method: 'PATCH',
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occured while updating dataset',
  })

export const useDeleteDataset = () =>
  useDeleteMutation({
    key,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while deleting the dataset.',
  })

const useDatasetTransition = (action: 'publish' | 'unpublish' | 'release' | 'unrelease') => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Dataset>, unknown, string>({
    mutationFn: (id: string) =>
      apiRequest<Dataset>({
        method: 'POST',
        endpoint: `/datasets/${id}/${action}`,
        headers: { 'x-api-request': 'true' },
        errorMessage: `An error occurred while trying to ${action} dataset`,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}

export const usePublishDataset = () => useDatasetTransition('publish')
export const useUnpublishDataset = () => useDatasetTransition('unpublish')
export const useReleaseDataset = () => useDatasetTransition('release')
export const useUnreleaseDataset = () => useDatasetTransition('unrelease')

export const useUpdatePublishedDatasetMeta = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Dataset>, unknown, DatasetUpdateApiData>({
    mutationFn: (data: DatasetUpdateApiData) =>
      apiRequest<Dataset>({
        method: 'PUT',
        endpoint: `/datasets/${data.id}/published/meta`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while updating published dataset metadata',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
}
