import { useMutation } from '@tanstack/react-query'
import { Method } from 'axios'

import { useCreateMutation } from '@/hooks/use-create-mutation'
import { useDataQuery } from '@/hooks/use-data-query'
import { WithId } from '@/types/common'
import {
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionPatchData,
} from '@/types/datastructures'

import { apiRequest, ApiServiceResponse } from '../../request/apiRequest'

type MutationFunctionInput<TData> = {
  data: TData
  endpoint: string
}

type UseDatastructureVersionMutationInput = {
  method: Method
  errorMessage?: string
}

export const useDatastructureVersionMutation = <TResponse, TData>({
  method,
  errorMessage,
}: UseDatastructureVersionMutationInput) => {
  return useMutation<ApiServiceResponse<TResponse>, unknown, MutationFunctionInput<TData>>({
    mutationFn: ({ data, ...options }: MutationFunctionInput<TData>) => {
      return apiRequest<TResponse>({
        method: method,
        endpoint: options.endpoint,
        headers: { 'x-api-request': 'true' },
        data: data,
        errorMessage,
      })
    },
  })
}

type UseGetDatastructureVersionOptions = {
  datastructureId: string
  versionId: string
  isEnabled?: boolean
}
export const useGetDatastructureVersion = ({
  datastructureId,
  versionId,
  isEnabled,
}: UseGetDatastructureVersionOptions) =>
  useDataQuery<DatastructureVersion>({
    id: versionId,
    key: `datastructures/${datastructureId}/versions`,
    isEnabled,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while fetching datastructure versions.',
  })

export const useCreateDatastructureVersion = () =>
  useDatastructureVersionMutation<DatastructureVersion, DatastructureVersionCreateData>({
    method: 'POST',
    errorMessage: 'An error occurred while creating datastructure version',
  })

export const useUpdateDatastructureVersion = () =>
  useDatastructureVersionMutation<DatastructureVersion, DatastructureVersionPatchData>({
    method: 'PATCH',
    errorMessage: 'An error occurred while updating datastructure version',
  })

export const useStatusUpdateDatastructureVersion = () =>
  useDatastructureVersionMutation<DatastructureVersion, undefined>({
    method: 'POST',
    errorMessage: 'An error occurred while changing the status of the datastructure version',
  })

export const useReleaseDatastructureVersion = (datastructureId: string) =>
  useCreateMutation<DatastructureVersion, WithId>({
    key: `datastructures/${datastructureId}/versions`,
    endpoint: (data: WithId) => `/datastructures/${datastructureId}/versions/${data.id}/release`,
    headers: { 'x-api-request': 'true' },
    errorMessage: 'An error occurred while releasing datastructure version',
  })

export const useUnreleaseDatastructureVersion = () =>
  useDatastructureVersionMutation<DatastructureVersion, WithId>({
    method: 'POST',
    errorMessage: 'An error occurred while unreleasing datastructure version',
  })
