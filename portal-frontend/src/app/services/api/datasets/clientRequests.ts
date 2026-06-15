import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'

import { apiRequest, ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDeleteMutation } from '@/hooks/use-delete-mutation'
import { useUpdateMutation } from '@/hooks/use-update-mutation'
import { Dataset, DatasetCreateApiData, DatasetPatchApiData, DatasetUpdateApiData } from '@/types/datasets'
import { NamedApi, NamedApiPayload } from '@/types/namedApis'

const key = 'datasets'

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

type CreateNamedApiInput = {
  datasetId: string
  api: NamedApiPayload
  existingApis: NamedApi[]
}

// PATCH /datasets/{id} merges namedApis by slug (backend PR #1315). No dedicated POST endpoint exists,
// so create-on-dataset goes through PATCH while presenting a standard create-mutation shape to callers.
export const useCreateNamedApi = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Dataset>, AxiosError, CreateNamedApiInput>({
    mutationFn: ({ datasetId, api, existingApis }) => {
      const existingInputs: NamedApiPayload[] = existingApis.map(a => ({
        name: a.name,
        slug: a.slug,
        standard: a.standard,
        version: a.version,
        description: a.description,
      }))
      return apiRequest<Dataset>({
        method: 'PATCH',
        endpoint: `/datasets/${datasetId}`,
        headers: { 'x-api-request': 'true' },
        data: { namedApis: [...existingInputs, api] },
        errorMessage: 'An error occurred while creating the API.',
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error creating named API:', error.message)
    },
  })
}

const useDatasetTransition = (action: 'stage' | 'unstage' | 'release' | 'unrelease') => {
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
    onError: error => {
      console.error(`Error during ${action}:`, (error as Error).message)
    },
  })
}

export const useStageDataset = () => useDatasetTransition('stage')
export const useUnstageDataset = () => useDatasetTransition('unstage')
export const useReleaseDataset = () => useDatasetTransition('release')
export const useUnreleaseDataset = () => useDatasetTransition('unrelease')

export const useUpdateReleasedDatasetMeta = () => {
  const queryClient = useQueryClient()
  return useMutation<ApiServiceResponse<Dataset>, unknown, DatasetUpdateApiData>({
    mutationFn: (data: DatasetUpdateApiData) =>
      apiRequest<Dataset>({
        method: 'PUT',
        endpoint: `/datasets/${data.id}/released/meta`,
        headers: { 'x-api-request': 'true' },
        data,
        errorMessage: 'An error occurred while updating released dataset metadata',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [key] })
    },
    onError: error => {
      console.error('Error updating released dataset metadata:', (error as Error).message)
    },
  })
}
